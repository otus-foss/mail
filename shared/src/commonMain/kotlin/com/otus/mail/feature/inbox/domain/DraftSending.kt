package com.otus.mail.feature.inbox.domain

import kotlin.time.Instant

/** The message a draft answers; [forwarded] when the draft passes it on rather than replying. */
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
enum class SendFailure { NoIdentity, Recipients, TooManyRecipients, TooLarge, NotPermitted, Unreachable, ServerProblem }

/** The failure a [DraftSender] result carries. */
class SendException(
    val failure: SendFailure,
    cause: Throwable? = null,
) : Exception("The message could not be sent: $failure", cause)

/** A message the server accepted: its stored copy [emailId] and its submission [submissionId]. */
data class SentMessage(
    val accountId: String,
    val emailId: String,
    val submissionId: String,
)

/** Sends drafts from the identity their From line names. */
fun interface DraftSender {
    /**
     * Sends [draft], files the sent copy in Sent, deletes the draft's saved copy, and marks
     * [answered] as answered or forwarded. With [at], the server holds the message until then and
     * the copy stays in Drafts, no longer marked as a draft, until it goes. A failed result holds a
     * [SendException] and leaves the saved copy in Drafts.
     */
    suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
        at: Instant?,
    ): Result<SentMessage>
}

/** Sending at a later time (RFC 8621, `maxDelayedSend`, with the RFC 4865 HOLDUNTIL parameter). */
interface ScheduledSends {
    /** How far ahead the account may schedule, in seconds; 0 when it cannot. */
    suspend fun maxDelaySeconds(accountId: String): Long

    /** Cancels a scheduled [message] and makes its copy a draft again. */
    suspend fun cancel(message: SentMessage): Result<Unit>
}
