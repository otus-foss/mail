package com.otus.mail.feature.compose.domain

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.core.messages.domain.answered
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Messages handed over for sending, kept in [store] until the server has them, so a closed app or
 * a dropped connection loses none. Each gets its own Message-ID, so after a send whose answer was
 * lost, [checker] can find out whether it went before it goes again. [wake] asks the platform to
 * run [resume] at a time even if the app is closed by then. [store] is read and written on [io].
 * Without a store, messages last as long as the process.
 */
class MailOutbox(
    store: OutboxStore?,
    private val sender: DraftSender,
    private val checker: SendChecker,
    private val wake: (Instant) -> Unit = {},
    private val now: () -> Instant = { Clock.System.now() },
    io: CoroutineContext = Dispatchers.IO,
) {
    // One send at a time, so Undo, a retry, and a background run never send the same message twice.
    private val lock = Mutex()
    private val entries = OutboxEntries(store, io)

    /** Keeps [draft] to go at [due], or at [sendAt] when the user chose a time, and returns its id. */
    suspend fun add(
        draft: Draft,
        answered: AnsweredMessage?,
        due: Instant,
        sendAt: Instant? = null,
    ): String {
        val id = newOutboxId()
        val entry =
            OutboxEntry(
                id,
                draft.copy(messageId = "$id@otus.mail"),
                answered,
                due.toEpochMilliseconds(),
                sendAt?.toEpochMilliseconds(),
            )
        lock.withLock { entries.save(entries.all() + entry) }
        // Later than the app itself sends it, so a background run only takes over from a closed app.
        wake(due + BACKGROUND_DELAY)
        return id
    }

    /**
     * Takes a message back, as on Undo; false when it already went. A message an earlier try may
     * have handed to the server stays as withdrawn, so a hold the server keeps is canceled once
     * it can be reached.
     */
    suspend fun cancel(id: String): Boolean =
        lock.withLock {
            val entry = entries.all().firstOrNull { it.id == id }
            when {
                entry == null -> Unit
                entry.attempted -> entries.replace(entry.copy(withdrawn = true, problem = null))
                else -> entries.removeIf { it.id == id }
            }
            entry != null
        }

    /** Forgets a message the user took back to edit, as from a "Couldn't send" toast. */
    suspend fun drop(id: String) {
        lock.withLock { entries.removeIf { it.id == id } }
    }

    /**
     * Sends message [id] now, or for the server to hold until [holdUntil]. A message an earlier try
     * may have sent is looked for first. The result holds the server's copy, or null when it went
     * before; a missing message counts as sent. A message stays after a failure: on a dropped
     * connection to go again by itself, otherwise with a [OutboxProblem] until the user acts.
     */
    suspend fun send(
        id: String,
        holdUntil: Instant? = null,
    ): Result<SentMessage?> =
        lock.withLock {
            val entry = entries.all().firstOrNull { it.id == id } ?: return@withLock Result.success(null)
            deliver(entry, holdUntil)
        }

    /** Sends each message that is due and needs no user, as after the app was closed or offline. */
    suspend fun resume() {
        entries
            .all()
            .filter { it.problem == null && it.dueMillis <= now().toEpochMilliseconds() }
            .forEach { send(it.id) }
    }

    /** The messages that need the user and were not offered before in this process, to offer now. */
    suspend fun offers(): List<OutboxEntry> = lock.withLock { entries.unoffered() }

    /** Drops [accountId]'s messages, as when the account is removed. */
    suspend fun forget(accountId: String) {
        lock.withLock { entries.removeIf { it.draft.accountId == accountId } }
    }

    /** Whether a message waits for the network, so a background run should try again. */
    suspend fun waiting(): Boolean = entries.all().any { it.problem == null && it.attempted }

    private suspend fun deliver(
        entry: OutboxEntry,
        holdUntil: Instant?,
    ): Result<SentMessage?> {
        val earlier = if (entry.attempted) earlierTry(entry) else null
        if (earlier != null) return earlier
        var trying = entry.copy(attempted = true, problem = null, storedCopy = null)
        entries.replace(trying)
        val time = entry.sendAt?.let(SendTime::Scheduled) ?: holdUntil?.let(SendTime::Held) ?: SendTime.Now
        val result =
            failSafe {
                sender.send(entry.draft, entry.answered, time) { emailId ->
                    // Kept before the copy is submitted, so a lost answer is looked up by this id.
                    trying = trying.copy(storedCopy = StoredCopy(emailId, now().toEpochMilliseconds()))
                    entries.replace(trying)
                }
            }
        val failure = (result.exceptionOrNull() as? SendException)?.failure
        when {
            result.isSuccess -> entries.removeIf { it.id == entry.id }

            failure == SendFailure.Mail(MailFailure.Unreachable) -> wake(now())

            // A clear refusal left nothing on the server, so the next try needs no lookup. A server
            // problem, such as a gateway error, may hide a lost answer, so that try looks first.
            else -> entries.fail(trying.copy(attempted = failure.mayHaveSent()), OutboxProblem.Refused)
        }
        return if (result.isSuccess || failure != null) {
            result
        } else {
            sendFailure(SendFailure.Mail(MailFailure.ServerProblem))
        }
    }

    /**
     * What the server says of an earlier try decides the outcome; null when the message can go. A
     * withdrawn message never goes: it leaves once the server answers, holding no copy to cancel.
     * A try that stored its copy is asked about only [SETTLE_TIME] after that, so a submission the
     * server is still working on when the answer was lost is not taken for a missing one. A
     * withdrawn one is asked at once, so a hold is canceled before its time comes.
     */
    private suspend fun earlierTry(entry: OutboxEntry): Result<SentMessage?>? {
        val unsettled =
            entry.storedCopy
                ?.takeUnless { entry.withdrawn }
                ?.let { Instant.fromEpochMilliseconds(it.atMillis) + SETTLE_TIME }
                ?.takeIf { now() < it }
        val check = if (unsettled != null) SendCheck.Unreachable else checker.lookUp(entry)
        if (check == SendCheck.Unreachable) wake(unsettled ?: now())
        return when (check) {
            SendCheck.Unreachable -> sendFailure(SendFailure.Mail(MailFailure.Unreachable))
            SendCheck.Sent -> entries.gone(entry)
            SendCheck.NotSent -> if (entry.withdrawn) entries.gone(entry) else null
            SendCheck.Unconfirmable -> if (entry.withdrawn) entries.gone(entry) else entries.unconfirmed(entry)
        }
    }
}

/** What this checker says of [entry]'s earlier try; one that cannot tell for anything it throws. */
private suspend fun SendChecker.lookUp(entry: OutboxEntry): SendCheck =
    try {
        check(entry.draft, entry.answered, cancelHeld = entry.withdrawn, storedCopy = entry.storedCopy?.emailId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SendCheck.Unconfirmable
    }

/** Forgets [entry], whose earlier try settled its outcome. */
private suspend fun OutboxEntries.gone(entry: OutboxEntry): Result<SentMessage?> {
    removeIf { it.id == entry.id }
    return Result.success(null)
}

/** Keeps [entry] for the user, since nobody can tell whether its earlier try went. */
private suspend fun OutboxEntries.unconfirmed(entry: OutboxEntry): Result<SentMessage?> {
    fail(entry, OutboxProblem.Unconfirmed)
    return sendFailure(SendFailure.Unconfirmed)
}

/**
 * [send]'s result, or a server problem for anything it throws but cancellation, so a message that
 * keeps failing waits for the user rather than crashing every start of the app.
 */
private suspend fun failSafe(send: suspend () -> Result<SentMessage>): Result<SentMessage> =
    try {
        send()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem)))
    }

private fun SendFailure?.mayHaveSent() = this == null || this == SendFailure.Mail(MailFailure.ServerProblem)

private fun sendFailure(failure: SendFailure) = Result.failure<SentMessage?>(SendException(failure))

private val BACKGROUND_DELAY = 30.seconds

/** Longer than a request may take, so the server has finished a submission whose answer was lost. */
private val SETTLE_TIME = 2.minutes

@OptIn(ExperimentalUuidApi::class)
private fun newOutboxId(): String = Uuid.random().toString()
