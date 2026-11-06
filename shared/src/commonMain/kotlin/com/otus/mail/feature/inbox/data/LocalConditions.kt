package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.messages.domain.ReadStatus
import com.otus.mail.core.messages.domain.RecipientField
import com.otus.mail.core.messages.domain.TypeFacet
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.bounds
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The check a cached row of a mailbox's unfiltered list must pass to show for [query] before the
 * server answers, the device's side of [emailConditions] for [loginId]. Null when a choice needs
 * what the device does not keep: a size, a Bcc recipient, list headers, or the Sent mail Awaiting
 * reply adds. The server's answer replaces these rows, so a match it finds and the device cannot
 * tell, such as a recipient's name, arrives with it. Choices only loaded threads can tell are
 * left to [InboxFilters.keepsLoaded], as for the server's rows.
 */
internal fun localCheck(
    query: MailQuery,
    loginId: String,
    now: Instant,
    zone: TimeZone,
): ((ListedRow) -> Boolean)? {
    val filters = query.filters
    val recipients = recipientAddresses(query, loginId)?.map { EmailAddressKey(it) }?.toSet()
    if (!filters.checkedOnDevice(recipients != null)) return null
    val dates = filters.date.bounds(now, zone)
    val from = filters.fromAddresses.map { EmailAddressKey(it) }.toSet()
    val readLater = query.readLaterIds[loginId]
    return { row ->
        val message = row.message
        filters.status.keeps(message) &&
            filters.quick.all { it.keeps(message, readLater) } &&
            (from.isEmpty() || message.senderAddresses.any { it in from }) &&
            (recipients == null || message.recipientAddresses(filters.recipientFields).any { it in recipients }) &&
            dates.after?.let { row.receivedAt >= it } != false &&
            dates.before?.let { row.receivedAt < it } != false
    }
}

private fun InboxFilters.checkedOnDevice(byRecipient: Boolean): Boolean =
    size.minBytes == null &&
        TypeFacet.Newsletters !in types &&
        !has(QuickFacet.AwaitingReply) &&
        !(byRecipient && RecipientField.Bcc in recipientFields)

private fun ReadStatus.keeps(message: MailMessage): Boolean =
    when (this) {
        ReadStatus.All -> true
        ReadStatus.Unread -> message.attributes.unread
        ReadStatus.Read -> !message.attributes.unread
    }

private fun QuickFacet.keeps(
    message: MailMessage,
    readLater: String?,
): Boolean =
    when (this) {
        QuickFacet.Flagged -> message.attributes.flagged
        QuickFacet.ReadLater -> readLater != null && readLater in message.location.mailboxIds
        QuickFacet.Attachments -> message.attributes.hasAttachment
        QuickFacet.Forwarded -> message.attributes.forwarded
        QuickFacet.NeedsMyReply, QuickFacet.AwaitingReply, QuickFacet.Conversations -> true
    }

private val MailMessage.senderAddresses: List<EmailAddressKey>
    get() = (listOf(sender) + conversation?.senders.orEmpty()).map { EmailAddressKey(it.address) }

private fun MailMessage.recipientAddresses(fields: Set<RecipientField>): List<EmailAddressKey> =
    fields
        .flatMap { field ->
            when (field) {
                RecipientField.To -> to
                RecipientField.Cc -> cc
                RecipientField.Bcc -> emptyList()
            }
        }.map { EmailAddressKey(it.address) }
