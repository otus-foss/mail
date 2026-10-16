package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.displayLine
import com.otus.mail.feature.inbox.domain.displayText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Maps one Email in [mailbox] of [account]. Ids are prefixed so accounts cannot collide. Server
 * text loses hidden characters here, so no screen can show a spoofed sender or subject, and an
 * HTML body becomes plain text. A part cut off before another part's text ends with [GAP_NOTE], so
 * the gap shows where it is; a cut at the end sets [MailMessage.bodyTruncated] instead.
 */
internal fun EmailObject.toMessage(
    accountId: String,
    account: String,
    mailbox: String,
    zone: TimeZone,
    accountAddress: String = "",
    mailboxId: String = "",
): MailMessage {
    val local = Instant.parse(receivedAt).toLocalDateTime(zone)
    val values = textBodyValues()
    val lastText = values.indexOfLast { it.value.isNotBlank() }
    val text =
        values.withIndex().joinToString("\n") { (index, part) ->
            if (part.isTruncated && index < lastText) "${part.value}\n$GAP_NOTE" else part.value
        }
    return MailMessage(
        id = "$accountId/$id",
        sender = from?.firstOrNull().toSender(accountId),
        subject = subject.orEmpty().displayLine(),
        body = text.ifBlank { preview }.displayText(),
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
                attachments = if (hasAttachment) attachments.orEmpty().map { it.displayName() } else emptyList(),
                flagged = keywords[FLAGGED_KEYWORD] == true,
            ),
        emailId = id,
        to = to.orEmpty().map { it.toSender(accountId) },
        cc = cc.orEmpty().map { it.toSender(accountId) },
        messageIds = messageId.orEmpty(),
        references = references.orEmpty(),
        bodyTruncated = values.drop(maxOf(lastText, 0)).any { it.isTruncated },
    )
}

/**
 * The value of each [EmailObject.textBody] part the server sent, HTML as readable text. The text
 * body holds the HTML part when a message has no plain alternative (RFC 8621, section 4.1.4).
 */
private fun EmailObject.textBodyValues(): List<EmailBodyValue> =
    textBody.orEmpty().mapNotNull { part ->
        part.partId?.let { bodyValues[it] }?.let { if (part.isHtml) it.copy(value = it.value.htmlToText()) else it }
    }

internal val EmailBodyPart.isHtml: Boolean
    get() = type.equals(HTML_TYPE, ignoreCase = true)

/** A blank display name shows the address instead. Both lose hidden characters. */
private fun EmailAddress?.toSender(accountId: String): MailSender {
    val address = this?.email.orEmpty().displayLine()
    return MailSender(this?.name?.displayLine()?.ifBlank { null } ?: address, address, accountId)
}

private fun EmailBodyPart.displayName(): String = name.orEmpty().displayLine().ifBlank { "Attachment" }

private fun Int.twoDigits() = toString().padStart(2, '0')

private const val HTML_TYPE = "text/html"

/** Marks where a cut-off part stops when more of the body follows it. */
private const val GAP_NOTE = "[Part of the message is missing here.]"
