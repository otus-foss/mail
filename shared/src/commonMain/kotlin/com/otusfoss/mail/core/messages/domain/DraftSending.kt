package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mail.MailFailure
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The message a draft answers; [forwarded] when the draft passes it on rather than replying. */
@Serializable
data class AnsweredMessage(
    val emailId: String,
    val forwarded: Boolean,
)

/**
 * The message a draft for this request answers in [accountId]. A follow-up adds to a message the
 * account sent, so it answers nothing, and an original from another account cannot be marked.
 */
fun ComposeRequest.answered(accountId: String): AnsweredMessage? {
    val answered =
        when (this) {
            is ComposeRequest.Reply -> AnsweredMessage(original.emailId, forwarded = false)
            is ComposeRequest.Forward -> AnsweredMessage(original.emailId, forwarded = true)
            is ComposeRequest.New, is ComposeRequest.FollowUp -> null
            is ComposeRequest.Resume -> return from.answered(accountId)
        }
    return answered?.takeIf { this.accountId == accountId }
}

/**
 * Why a draft was not sent. [MailFailure.Unreachable] means the outcome is unknown: the server may
 * have sent the message before the connection failed.
 */
sealed interface SendFailure {
    data object NoIdentity : SendFailure

    data object Recipients : SendFailure

    data object TooManyRecipients : SendFailure

    data object TooLarge : SendFailure

    /** A send may have gone, and the server cannot say whether it did, so only the user can tell. */
    data object Unconfirmed : SendFailure

    /** The server refused to cancel a send that goes whatever happens, or one already canceled. */
    data object CannotUnsend : SendFailure

    data class Mail(
        val failure: MailFailure,
    ) : SendFailure
}

/** The failure a [DraftSender] result carries. */
class SendException(
    val failure: SendFailure,
    cause: Throwable? = null,
) : Exception("The message could not be sent: $failure", cause)

/**
 * A message the server accepted: its stored copy [emailId] and its submission [submissionId], sent
 * as [identityId]. A canceled send moves the copy back to [draftsId], the account's Drafts folder
 * when it has one, and takes back the answered or forwarded mark this send gave [marked].
 */
data class SentMessage(
    val accountId: String,
    val emailId: String,
    val submissionId: String,
    val draftsId: String? = null,
    val identityId: String = "",
    val marked: AnsweredMessage? = null,
)

/** Time for the request to reach the server and for its clock to differ from the device's. */
internal val SERVER_SPARE = 2.seconds

/** When the server sends a message, and where its copy waits until then. */
sealed interface SendTime {
    /** At once; the copy moves to Sent. */
    data object Now : SendTime

    /** At [at], so Undo can still cancel it until then; the copy moves to Sent at once. */
    data class Held(
        val at: Instant,
    ) : SendTime

    /** At [at], a time the user chose; the copy waits in Scheduled, without `$draft`, until then. */
    data class Scheduled(
        val at: Instant,
    ) : SendTime
}

/** Sends drafts from the identity their From line names. */
fun interface DraftSender {
    /**
     * Sends [draft] at [time], files its copy as [time] says, deletes the draft's saved copy, and
     * marks [answered] as answered or forwarded. A failed result holds a [SendException] and leaves
     * the saved copy in Drafts.
     */
    suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        time: SendTime,
    ): Result<SentMessage>

    /**
     * [send], telling [stored] the id of the copy the server stored before that copy is submitted,
     * so a send whose answer is lost can be checked by that id. A sender that stores and submits
     * in one step never calls it.
     */
    suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        time: SendTime,
        stored: suspend (emailId: String) -> Unit,
    ): Result<SentMessage> = send(draft, answered, time)
}

/** Sending at a later time (RFC 8621, `maxDelayedSend`, with the RFC 4865 HOLDUNTIL parameter). */
interface ScheduledSends {
    /** How far ahead the account may schedule, in seconds; 0 when it cannot. */
    suspend fun maxDelaySeconds(accountId: String): Long

    /**
     * Cancels a held or scheduled [message] and makes its copy a draft in Drafts again. When the
     * server does not confirm the cancel, it asks what became of the send before it answers.
     */
    suspend fun cancel(message: SentMessage): SendCancel

    /**
     * Sends a held or scheduled [message] at once. It fails only when the message will not go:
     * the hold was canceled but the new submission refused, which makes the copy a draft again.
     */
    suspend fun sendNow(message: SentMessage): Result<Unit>

    /** The send still waiting for [emailId] of [accountId]; null when none waits or the server cannot say. */
    suspend fun held(
        accountId: String,
        emailId: String,
    ): HeldSend?

    /** Moves [accountId]'s scheduled mail that the server has sent from Scheduled to Sent. */
    suspend fun fileSent(accountId: String)
}

/** What became of a held or scheduled send that [ScheduledSends.cancel] tried to cancel. */
sealed interface SendCancel {
    /** It will not go. Its copy is a draft again, unless restoring it failed, which a later filing repairs. */
    data object Stopped : SendCancel

    /** It went, or goes whatever happens, so the mail can no longer be changed. */
    data object Final : SendCancel

    /** It still waits, or the server could not be asked; trying again may settle it. */
    data object Waiting : SendCancel

    /** The server no longer knows it, so only the user can tell whether it went. */
    data object Unknown : SendCancel
}

/** A [message] the server holds until [at]. */
data class HeldSend(
    val message: SentMessage,
    val at: Instant,
)

/** Sending was canceled, but the saved email could not be restored as an editable draft. */
class DraftRestoreException(
    cause: Throwable? = null,
) : Exception("Send canceled; draft restoration failed", cause)
