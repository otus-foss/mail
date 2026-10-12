package com.otus.mail.feature.inbox.domain

/** What a new draft starts from: a blank message, a reply, or a forward. */
sealed interface ComposeRequest {
    /** A blank message, from [accountId] when given, else the first account that can send. */
    data class New(
        val accountId: String? = null,
    ) : ComposeRequest

    /** An answer to [original]: its sender, or with [all] everyone else it went to as well. */
    data class Reply(
        val original: OriginalMessage,
        val all: Boolean,
    ) : ComposeRequest

    /** [original] passed on to new recipients. */
    data class Forward(
        val original: OriginalMessage,
    ) : ComposeRequest
}

/**
 * The parts of a received message a reply or forward needs; [MailMessage.original] builds it.
 * [messageIds] and [references] are the RFC 5322 Message-ID and References values that keep the
 * answer in its thread.
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
        sentLabel = "${received.date} ${received.time}",
        body = body,
        messageIds = messageIds,
        references = references,
    )
