package com.otus.mail.feature.inbox.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Messages handed over for sending, kept in [store] until the server has them, so a closed app or
 * a dropped connection loses none. Each gets its own Message-ID, so after a send whose answer was
 * lost, [checker] can find out whether it went before it goes again. [wake] asks the platform to
 * run [resume] at a time even if the app is closed by then. Without a store, messages last as long
 * as the process.
 */
class MailOutbox(
    private val store: OutboxStore?,
    private val sender: DraftSender,
    private val checker: SendChecker,
    private val wake: (Instant) -> Unit = {},
    private val now: () -> Instant = { Clock.System.now() },
) {
    // One send at a time, so Undo, a retry, and a background run never send the same message twice.
    private val lock = Mutex()
    private var memory = emptyList<OutboxEntry>()

    // Messages already offered to the user, so a recreated screen does not offer them twice.
    private var offered = emptySet<String>()

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
        lock.withLock { save(entries() + entry) }
        // Later than the app itself sends it, so a background run only takes over from a closed app.
        wake(due + BACKGROUND_DELAY)
        return id
    }

    /** Drops a message before it goes, as on Undo; false when it already went or is on its way. */
    suspend fun cancel(id: String): Boolean =
        lock.withLock {
            val entries = entries()
            val waiting = entries.any { it.id == id && !it.attempted }
            if (waiting) save(entries.filterNot { it.id == id })
            waiting
        }

    /** Forgets a message the user took back to edit, as from a "Couldn't send" toast. */
    suspend fun drop(id: String) {
        lock.withLock { save(entries().filterNot { it.id == id }) }
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
            val entry = entries().firstOrNull { it.id == id } ?: return@withLock Result.success(null)
            deliver(entry, holdUntil)
        }

    /** Sends each message that is due and needs no user, as after the app was closed or offline. */
    suspend fun resume() {
        entries()
            .filter { it.problem == null && it.dueMillis <= now().toEpochMilliseconds() }
            .forEach { send(it.id) }
    }

    /** The messages that need the user and were not offered before in this process, to offer now. */
    suspend fun offers(): List<OutboxEntry> =
        lock.withLock {
            val found = entries().filter { it.problem != null && it.id !in offered }
            offered = offered + found.map { it.id }
            found
        }

    /** Drops [accountId]'s messages, as when the account is removed. */
    suspend fun forget(accountId: String) {
        lock.withLock { save(entries().filterNot { it.draft.accountId == accountId }) }
    }

    /** Whether a message waits for the network, so a background run should try again. */
    suspend fun waiting(): Boolean = entries().any { it.problem == null && it.attempted }

    private suspend fun deliver(
        entry: OutboxEntry,
        holdUntil: Instant?,
    ): Result<SentMessage?> {
        val earlier = if (entry.attempted) earlierTry(entry) else null
        if (earlier != null) return earlier
        replace(entry.copy(attempted = true, problem = null))
        val time = entry.sendAt?.let(SendTime::Scheduled) ?: holdUntil?.let(SendTime::Held) ?: SendTime.Now
        val result = sender.send(entry.draft, entry.answered, time)
        val failure = (result.exceptionOrNull() as? SendException)?.failure
        if (result.isSuccess) {
            remove(entry.id)
            return result
        }
        if (failure == SendFailure.Unreachable) return unreachable()
        // A refused send left nothing on the server, so the next try needs no lookup.
        return failed(entry.copy(attempted = false), OutboxProblem.Refused, failure ?: SendFailure.ServerProblem)
    }

    /** What the server says of an earlier try decides the outcome; null when the message can go. */
    private suspend fun earlierTry(entry: OutboxEntry): Result<SentMessage?>? =
        when (checker.check(entry.draft)) {
            SendCheck.Sent -> {
                remove(entry.id)
                Result.success(null)
            }

            SendCheck.NotSent -> {
                null
            }

            SendCheck.Unreachable -> {
                unreachable()
            }

            SendCheck.Unconfirmable -> {
                failed(entry, OutboxProblem.Unconfirmed, SendFailure.Unreachable)
            }
        }

    private fun unreachable(): Result<SentMessage?> {
        wake(now())
        return Result.failure(SendException(SendFailure.Unreachable))
    }

    private suspend fun failed(
        entry: OutboxEntry,
        problem: OutboxProblem,
        failure: SendFailure,
    ): Result<SentMessage?> {
        replace(entry.copy(problem = problem))
        // The send's own caller tells the user, so it is not offered again.
        offered = offered + entry.id
        return Result.failure(SendException(failure))
    }

    private suspend fun replace(entry: OutboxEntry) = save(entries().map { if (it.id == entry.id) entry else it })

    private suspend fun remove(id: String) = save(entries().filterNot { it.id == id })

    private suspend fun entries(): List<OutboxEntry> {
        val saved = store ?: return memory
        return withContext(Dispatchers.IO) { OutboxEntry.decode(saved.read()[OutboxStore.ENTRIES]) }
    }

    private suspend fun save(entries: List<OutboxEntry>) {
        memory = entries
        val saved = store ?: return
        withContext(Dispatchers.IO) { saved.write(mapOf(OutboxStore.ENTRIES to OutboxEntry.encode(entries))) }
    }
}

private val BACKGROUND_DELAY = 30.seconds

@OptIn(ExperimentalUuidApi::class)
private fun newOutboxId(): String = Uuid.random().toString()
