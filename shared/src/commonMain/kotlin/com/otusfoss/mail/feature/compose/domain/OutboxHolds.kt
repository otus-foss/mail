package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.SERVER_SPARE
import com.otusfoss.mail.core.messages.domain.ScheduledSends
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SentMessage
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What the outbox does with a message the user took back, or one the server holds for Undo. Each
 * step keeps what the user asked in [entries] before it asks the server, so it can run again after
 * a lost answer, from the app or from a background run.
 */
internal class OutboxHolds(
    private val entries: OutboxEntries,
    private val scheduled: ScheduledSends,
    private val checker: SendChecker,
    private val wake: (Instant) -> Unit,
    private val now: () -> Instant,
) {
    // What became of each message taken back in this process, for an Undo that asks again later.
    private var outcomes = emptyMap<String, SendCancel>()

    /** What became of message [id], which left the outbox when it was taken back; null when it was not. */
    fun takenBack(id: String): SendCancel? = outcomes[id]

    /**
     * Takes [entry] back. One that no try handed to the server just leaves. Any other is kept as
     * withdrawn and due at once, and the server is asked to stop it. A hold the server confirmed is
     * canceled, which makes its copy a draft again. A hold whose request got no answer is looked
     * up, canceled, and its copy destroyed. The message leaves once the server answered; until then
     * a background run is asked for.
     */
    suspend fun withdraw(entry: OutboxEntry): SendCancel {
        val dueNow = minOf(entry.dueMillis, now().toEpochMilliseconds())
        val withdrawn = entry.copy(withdrawn = true, sendNow = false, problem = null, dueMillis = dueNow)
        if (entry.attempted && withdrawn != entry) entries.replace(withdrawn)
        val outcome =
            when {
                !entry.attempted -> SendCancel.Stopped
                entry.held != null -> cancel(entry.held)
                else -> checker.lookUp(withdrawn).asCancel()
            }
        if (outcome == SendCancel.Waiting) wake(now()) else leave(entry, outcome)
        return outcome
    }

    private suspend fun leave(
        entry: OutboxEntry,
        outcome: SendCancel,
    ) {
        entries.remove(entry.id)
        outcomes = outcomes + (entry.id to outcome)
    }

    /**
     * Sends [entry], which the server holds, at once: the hold is canceled and the copy submitted
     * again. With little of the hold left the server sends on time instead. A try that already
     * asked for the cancel cannot count on that, so it asks again whatever the time. Offline, the
     * message stays to go again. A refused one stays for the user with its copy, a draft again; an
     * unconfirmed one stays apart from its copy, which may be the sent message.
     */
    suspend fun sendNow(entry: OutboxEntry): Result<Unit> {
        val held = entry.held ?: return entry.problem.asResult()
        val onTime = !entry.sendNow && entry.due - now() <= SEND_EARLY_MIN
        if (onTime) entries.remove(entry.id)
        return if (onTime) Result.success(Unit) else resubmit(entry, held)
    }

    private suspend fun resubmit(
        entry: OutboxEntry,
        held: SentMessage,
    ): Result<Unit> {
        if (!entry.sendNow) entries.replace(entry.copy(sendNow = true))
        val result = failSafe { scheduled.sendNow(held) }
        val failure = (result.exceptionOrNull() as? SendException)?.failure
        val settled = entry.copy(held = null, sendNow = false)
        when {
            result.isSuccess -> entries.remove(entry.id)
            failure == SendFailure.Mail(MailFailure.Unreachable) -> wake(now())
            failure == SendFailure.Unconfirmed -> entries.fail(settled.withCopy(null), OutboxProblem.Unconfirmed)
            else -> entries.fail(settled.copy(attempted = false).withCopy(held.emailId), OutboxProblem.Refused)
        }
        return result
    }

    /**
     * What [entry], which the server holds, needs without the user: to go early when that was
     * asked, and to leave once its hold is over.
     */
    suspend fun resume(entry: OutboxEntry) {
        if (entry.sendNow) sendNow(entry)
        if (!entry.sendNow && entry.due + SERVER_SPARE <= now()) entries.remove(entry.id)
    }

    /** [ScheduledSends.cancel]; a send only the user can tell about for anything it throws. */
    private suspend fun cancel(held: SentMessage): SendCancel =
        try {
            scheduled.cancel(held)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SendCancel.Unknown
        }
}

private fun OutboxEntry.withCopy(emailId: String?) = copy(draft = draft.copy(emailId = emailId))

/** What the lookup of a withdrawn message means for its Undo. */
private fun SendCheck.asCancel(): SendCancel =
    when (this) {
        SendCheck.NotSent -> SendCancel.Stopped
        SendCheck.Sent -> SendCancel.Final
        SendCheck.Unreachable -> SendCancel.Waiting
        SendCheck.Unconfirmable -> SendCancel.Unknown
    }

/** What sending early says of a message the server no longer holds: it went, unless it has a problem. */
private fun OutboxProblem?.asResult(): Result<Unit> =
    when (this) {
        null -> Result.success(Unit)
        OutboxProblem.Refused -> Result.failure(SendException(SendFailure.Mail(MailFailure.ServerProblem)))
        OutboxProblem.Unconfirmed -> Result.failure(SendException(SendFailure.Unconfirmed))
    }

/** Below this much of the Undo window left, sending early lets the server send on time. */
private val SEND_EARLY_MIN = 3.seconds
