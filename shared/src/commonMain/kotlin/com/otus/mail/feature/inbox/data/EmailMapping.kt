package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Maps one Email in [mailbox] of [account]. Ids are prefixed so accounts cannot collide. */
internal fun EmailObject.toMessage(
    accountId: String,
    account: String,
    mailbox: String,
    zone: TimeZone,
    accountAddress: String = "",
    mailboxId: String = "",
): MailMessage {
    val sender = from?.firstOrNull()
    val local = Instant.parse(receivedAt).toLocalDateTime(zone)
    val text = textBody.orEmpty().mapNotNull { part -> part.partId?.let { bodyValues[it]?.value } }.joinToString("\n")
    return MailMessage(
        id = "$accountId/$id",
        sender =
            MailSender(
                sender?.name?.ifBlank { null } ?: sender?.email.orEmpty(),
                sender?.email.orEmpty(),
                accountId,
            ),
        subject = subject.orEmpty(),
        body = text.ifBlank { preview },
        received = ReceivedAt(local.date.toString(), "${local.hour.twoDigits()}:${local.minute.twoDigits()}"),
        location =
            MailLocation(
                mailbox,
                account,
                accountId,
                accountAddress,
                mailboxId = mailboxId,
                mailboxIds = mailboxIds.filterValues { it }.keys,
            ),
        attributes =
            MailAttributes(
                unread = keywords[SEEN_KEYWORD] != true,
                attachments = if (hasAttachment) attachments.orEmpty().map { it.name ?: "Attachment" } else emptyList(),
                flagged = keywords[FLAGGED_KEYWORD] == true,
            ),
        emailId = id,
    )
}

private fun Int.twoDigits() = toString().padStart(2, '0')
