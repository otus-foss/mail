package com.otus.mail.feature.inbox.domain

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

/** Sends drafts from the identity their From line names. */
fun interface DraftSender {
    /**
     * Sends [draft], files the sent copy in Sent, deletes the draft's saved copy, and marks
     * [answered] as answered or forwarded. A failed result holds a [SendException] and leaves the
     * saved copy in Drafts.
     */
    suspend fun send(
        draft: Draft,
        answered: AnsweredMessage?,
    ): Result<Unit>
}
