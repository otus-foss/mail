package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.messages.domain.SentMessage
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How the outbox sends one message through [sender]. A try that may have reached the server is
 * looked up with [checker] before the message goes again, and every outcome is kept in [entries]
 * before this returns.
 */
internal class OutboxSends(
    private val entries: OutboxEntries,
    private val sender: DraftSender,
    private val checker: SendChecker,
    private val wake: (Instant) -> Unit,
    private val now: () -> Instant,
) {
    /** Sends [entry] now, or for the server to hold until [holdUntil]; see [MailOutbox.send]. */
    suspend fun deliver(
        entry: OutboxEntry,
        holdUntil: Instant?,
    ): Result<SentMessage?> {
        when (if (entry.attempted) earlierTry(entry) else SendCheck.NotSent) {
            SendCheck.Unreachable -> return sendFailure(SendFailure.Mail(MailFailure.Unreachable))
            SendCheck.Sent -> return entries.gone(entry)
            SendCheck.Unconfirmable -> return entries.unconfirmed(entry)
            SendCheck.NotSent -> Unit
        }
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
            // A held message stays until its hold is over, so Undo and sending it early can find it.
            time is SendTime.Held && result.isSuccess -> entries.replace(trying.copy(held = result.getOrNull()))

            result.isSuccess -> entries.remove(entry.id)

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
     * Asks the server about [entry], whose failed send may have gone all the same, before anyone
     * offers it as not sent. A message that went leaves. One that did not stays for the user, and
     * its next try needs no lookup. While the server cannot be asked, it stays as it is.
     */
    suspend fun confirm(entry: OutboxEntry) {
        when (earlierTry(entry)) {
            SendCheck.Unreachable -> Unit
            SendCheck.Sent -> entries.remove(entry.id)
            SendCheck.NotSent -> entries.replace(entry.copy(attempted = false, storedCopy = null))
            SendCheck.Unconfirmable -> entries.replace(entry.copy(problem = OutboxProblem.Unconfirmed))
        }
    }

    /**
     * What the server says of [entry]'s earlier try. A try that stored its copy is asked about only
     * [SETTLE_TIME] after that, so a submission the server is still working on when the answer was
     * lost is not taken for a missing one; until then, and while the server cannot be reached, a
     * background run is asked for.
     */
    private suspend fun earlierTry(entry: OutboxEntry): SendCheck {
        val unsettled =
            entry.storedCopy
                ?.let { Instant.fromEpochMilliseconds(it.atMillis) + SETTLE_TIME }
                ?.takeIf { now() < it }
        val check = if (unsettled != null) SendCheck.Unreachable else checker.lookUp(entry)
        if (check == SendCheck.Unreachable) wake(unsettled ?: now())
        return check
    }
}

/** What this checker says of [entry]'s earlier try; one that cannot tell for anything it throws. */
internal suspend fun SendChecker.lookUp(entry: OutboxEntry): SendCheck =
    try {
        check(entry.draft, entry.answered, cancelHeld = entry.withdrawn, storedCopy = entry.storedCopy?.emailId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SendCheck.Unconfirmable
    }

/** Forgets [entry], whose earlier try settled its outcome. */
private suspend fun OutboxEntries.gone(entry: OutboxEntry): Result<SentMessage?> {
    remove(entry.id)
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
internal suspend fun <T> failSafe(send: suspend () -> Result<T>): Result<T> =
    try {
        send()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem)))
    }

private fun SendFailure?.mayHaveSent() = this == null || this == SendFailure.Mail(MailFailure.ServerProblem)

private fun sendFailure(failure: SendFailure) = Result.failure<SentMessage?>(SendException(failure))

/** Longer than a request may take, so the server has finished a submission whose answer was lost. */
private val SETTLE_TIME = 2.minutes
