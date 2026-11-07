package com.otus.mail.core.messages.data

import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.isAnswered
import com.otus.mail.core.mail.isFlagged
import com.otus.mail.core.mail.isForwarded
import com.otus.mail.core.mail.isSeen
import com.otus.mail.core.mail.receivedTime
import com.otus.mail.core.messages.domain.MailAttributes
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.displayLine
import com.otus.mail.core.messages.domain.displayText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Maps one Email listed at [place], whose mailboxes it fills in. Ids are prefixed with the account
 * so accounts cannot collide. Server text loses hidden characters here, so no screen can show a
 * spoofed sender or subject. The body is the server's preview until the message opens and its
 * body loads.
 */
internal fun EmailObject.toMessage(
    place: MailLocation,
    zone: TimeZone,
    serverSite: String = "",
): MailMessage {
    val accountId = place.accountId
    val shortText = preview.displayText()
    return MailMessage(
        id = "$accountId/$id",
        sender = from?.firstOrNull().toSender(accountId).withBrand(authenticationResults, serverSite),
        subject = subject.orEmpty().displayLine(),
        body = shortText,
        received = receivedAt(receivedTime, zone),
        location = place.copy(mailboxIds = mailboxIds.filterValues { it }.keys),
        attributes =
            MailAttributes(
                unread = !isSeen,
                attachments = if (hasAttachment) attachments.orEmpty().map { it.displayName() } else emptyList(),
                flagged = isFlagged,
                invite = hasInvite,
                answered = isAnswered,
                forwarded = isForwarded,
                hasAttachment = hasFile,
            ),
        emailId = id,
        to = to.orEmpty().map { it.toSender(accountId) },
        cc = cc.orEmpty().map { it.toSender(accountId) },
        bcc = bcc.orEmpty().map { it.toSender(accountId) },
        replyTo = replyTo.orEmpty().map { it.toSender(accountId) },
        messageIds = messageId.orEmpty(),
        references = references.orEmpty(),
        preview = shortText,
    )
}

/** When an email arrived, as the date and minute in [zone]. */
internal fun receivedAt(
    value: Instant,
    zone: TimeZone,
): ReceivedAt {
    val local = value.toLocalDateTime(zone)
    return ReceivedAt(local.date.toString(), "${local.hour.twoDigits()}:${local.minute.twoDigits()}")
}

internal val EmailBodyPart.isHtml: Boolean
    get() = type.equals(HTML_TYPE, ignoreCase = true)

/** A blank display name shows the address instead. Both lose hidden characters. */
internal fun EmailAddress?.toSender(accountId: String): MailSender {
    val address = this?.email.orEmpty().displayLine()
    return MailSender(this?.name?.displayLine()?.ifBlank { null } ?: address, address, accountId)
}

private fun MailSender.withBrand(
    authenticationResults: List<String>?,
    serverSite: String,
) = copy(brandDomain = dmarcPassDomain(authenticationResults, address, serverSite))

private fun EmailBodyPart.displayName(): String = name.orEmpty().displayLine().ifBlank { "Attachment" }

private fun Int.twoDigits() = toString().padStart(2, '0')

private const val HTML_TYPE = "text/html"
