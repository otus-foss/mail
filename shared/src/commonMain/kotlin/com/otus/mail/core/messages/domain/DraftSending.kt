package com.otus.mail.core.messages.domain

import kotlinx.serialization.Serializable
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
 * Why a draft was not sent. [Unreachable] means the outcome is unknown: the server may have sent
 * the message before the connection failed.
 */
enum class SendFailure {
    NoIdentity,
    Recipients,
    TooManyRecipients,
    TooLarge,
    NotPermitted,
    Unreachable,
    ServerProblem,

    /** A send may have gone, and the server cannot say whether it did, so only the user can tell. */
    Unconfirmed,
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

/** When the server sends a message, and where its copy waits until then. */
sealed interface SendTime {
    /** At once; the copy moves to Sent. */
    data object Now : SendTime

    /** At [at], so Undo can still cancel it until then; the copy moves to Sent at once. */
    data class Held(
        val at: Instant,
    ) : SendTime

    /** At [at], a time the user chose; the copy stays in Drafts, without `$draft`, until then. */
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

    /** Cancels a held or scheduled [message] and makes its copy a draft in Drafts again. */
    suspend fun cancel(message: SentMessage): Result<Unit>

    /**
     * Sends a held or scheduled [message] at once. It fails only when the message will not go:
     * the hold was canceled but the new submission refused, which makes the copy a draft again.
     */
    suspend fun sendNow(message: SentMessage): Result<Unit>
}

/** Sending was canceled, but the saved email could not be restored as an editable draft. */
class DraftRestoreException(
    cause: Throwable? = null,
) : Exception("Send canceled; draft restoration failed", cause)
