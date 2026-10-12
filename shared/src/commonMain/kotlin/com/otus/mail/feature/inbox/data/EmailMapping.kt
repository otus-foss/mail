package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailAddress
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
    val local = Instant.parse(receivedAt).toLocalDateTime(zone)
    val text = textBody.orEmpty().mapNotNull { part -> part.partId?.let { bodyValues[it]?.value } }.joinToString("\n")
    return MailMessage(
        id = "$accountId/$id",
        sender = from?.firstOrNull().toSender(accountId),
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
        to = to.orEmpty().map { it.toSender(accountId) },
        cc = cc.orEmpty().map { it.toSender(accountId) },
        messageIds = messageId.orEmpty(),
        references = references.orEmpty(),
    )
}

/** A blank display name shows the address instead. */
private fun EmailAddress?.toSender(accountId: String) =
    MailSender(this?.name?.ifBlank { null } ?: this?.email.orEmpty(), this?.email.orEmpty(), accountId)

private fun Int.twoDigits() = toString().padStart(2, '0')
