package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.files.PickedFile
import kotlinx.datetime.LocalDateTime

/** What a draft starts from: a new message, a reply, a follow-up, a forward, or an earlier draft. */
sealed interface ComposeRequest {
    /**
     * A new message from [accountId] when given, else the first account that can send. It goes [to]
     * these people, such as contacts picked on the Contacts page. A `mailto:` link can also fill in
     * [cc], [bcc], the [subject], and a plain-text [body]; see [mailtoRequest].
     */
    data class New(
        val accountId: String? = null,
        val to: List<MailSender> = emptyList(),
        val cc: List<MailSender> = emptyList(),
        val bcc: List<MailSender> = emptyList(),
        val subject: String = "",
        val body: String = "",
    ) : ComposeRequest

    /** An answer to [original]: its sender, or with [all] everyone else it went to as well. */
    data class Reply(
        val original: OriginalMessage,
        val all: Boolean,
    ) : ComposeRequest

    /** Another message to the people [original] went to, which the account sent. */
    data class FollowUp(
        val original: OriginalMessage,
    ) : ComposeRequest

    /** [original] passed on to new recipients. */
    data class Forward(
        val original: OriginalMessage,
    ) : ComposeRequest

    /**
     * A [draft] opened again as it was, such as after Undo on a discarded one; [from] says what it
     * began as, and [sendAt] the time it was scheduled for, which Send keeps. A draft [asSaved] is
     * its stored copy exactly, so leaving Write without a change saves nothing. One that edits a
     * scheduled send names it as [held]: the send keeps waiting unchanged until Send replaces it.
     */
    data class Resume(
        val draft: Draft,
        val from: ComposeRequest,
        val attachmentSources: Map<String, PickedFile> = emptyMap(),
        val sendAt: LocalDateTime? = null,
        val asSaved: Boolean = false,
        val held: SentMessage? = null,
    ) : ComposeRequest
}

/**
 * The parts of a received message a reply or forward needs; [MailMessage.original] builds it.
 * [messageIds] and [references] are the RFC 5322 Message-ID and References values that keep the
 * answer in its thread. [bodyTruncated] when [body] is only the start of the message.
 */
data class OriginalMessage(
    val accountId: String,
    val emailId: String,
    val subject: String,
    val from: MailSender,
    val to: List<MailSender>,
    val cc: List<MailSender>,
    val sentLabel: String,
    val body: String,
    val messageIds: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val bodyTruncated: Boolean = false,
    val replyTo: List<MailSender> = emptyList(),
)

/** The parts of this message a reply or forward quotes and threads with. */
fun MailMessage.original(): OriginalMessage =
    OriginalMessage(
        accountId = location.accountId,
        emailId = emailId,
        subject = subject,
        from = sender,
        to = to,
        cc = cc,
        replyTo = replyTo,
        sentLabel = "${received.date} ${received.time}",
        body = body,
        messageIds = messageIds,
        references = references,
        bodyTruncated = bodyTruncated,
    )

/** The message a reply or follow-up answers, whose thread it shows. */
internal val ComposeRequest.answeredOriginal: OriginalMessage?
    get() =
        when (this) {
            is ComposeRequest.Reply -> original
            is ComposeRequest.FollowUp -> original
            is ComposeRequest.Resume -> from.answeredOriginal
            is ComposeRequest.New, is ComposeRequest.Forward -> null
        }

/** The account a draft for this request comes from, when the request names one. */
val ComposeRequest.accountId: String?
    get() =
        when (this) {
            is ComposeRequest.New -> accountId
            is ComposeRequest.Reply -> original.accountId
            is ComposeRequest.FollowUp -> original.accountId
            is ComposeRequest.Forward -> original.accountId
            is ComposeRequest.Resume -> draft.accountId
        }
