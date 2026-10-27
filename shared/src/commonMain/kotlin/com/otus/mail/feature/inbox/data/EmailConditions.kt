package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapSort
import com.otus.mail.core.mail.anyOf
import com.otus.mail.core.mail.condition
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.operator
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.TypeFacet
import com.otus.mail.feature.inbox.domain.bounds
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * The `Email/query` conditions (RFC 8621, section 4.4.1) [query] puts on the mail of [loginId],
 * all of which must hold; null when the query reads none of that login's mail. Mailboxes are left
 * to the caller. Needs my reply, Awaiting reply, Conversations, and Calendar invites have no
 * condition; the caller checks them on the loaded threads.
 */
internal fun emailConditions(
    query: MailQuery,
    loginId: String,
    now: Instant,
    zone: TimeZone,
): List<JsonObject>? {
    if (!query.reads(loginId)) return null
    val filters = query.filters
    val dates = filters.date.bounds(now, zone)
    return buildList {
        filters.status.condition()?.let(::add)
        filters.quick.sortedBy { it.ordinal }.mapNotNullTo(this) { it.condition(query, loginId) }
        if (TypeFacet.Newsletters in filters.types) add(anyOf(LIST_HEADERS.map { header(it) }))
        filters.fromAddresses.takeIf { it.isNotEmpty() }?.let { from ->
            add(anyOf(from.sorted().map { condition("from", JsonPrimitive(it)) }))
        }
        recipientCondition(query, loginId)?.let(::add)
        dates.after?.let { add(condition("after", JsonPrimitive(it.utcDate()))) }
        dates.before?.let { add(condition("before", JsonPrimitive(it.utcDate()))) }
        filters.size.minBytes?.let { add(condition("minSize", JsonPrimitive(it))) }
    }
}

private fun ReadStatus.condition(): JsonObject? =
    when (this) {
        ReadStatus.All -> null
        ReadStatus.Unread -> keyword("notKeyword", SEEN)
        ReadStatus.Read -> keyword("hasKeyword", SEEN)
    }

/** This choice's condition for [loginId]; null for the choices the caller checks on loaded threads. */
private fun QuickFacet.condition(
    query: MailQuery,
    loginId: String,
): JsonObject? =
    when (this) {
        QuickFacet.Flagged -> keyword("hasKeyword", FLAGGED)
        QuickFacet.ReadLater -> inMailbox(query.readLaterIds.getValue(loginId))
        QuickFacet.Attachments -> condition("hasAttachment", JsonPrimitive(true))
        QuickFacet.Forwarded -> keyword("hasKeyword", FORWARDED)
        QuickFacet.NeedsMyReply, QuickFacet.AwaitingReply, QuickFacet.Conversations -> null
    }

/** Mail to the [recipientAddresses] in the [recipientFields]; null when no recipient is chosen. */
private fun recipientCondition(
    query: MailQuery,
    loginId: String,
): JsonObject? {
    val addresses = recipientAddresses(query, loginId) ?: return null
    val fields = query.filters.recipientFields.sortedBy { it.ordinal }
    return anyOf(addresses.flatMap { address -> fields.map { condition(it.jmapName, JsonPrimitive(address)) } })
}

/**
 * The recipients [query] asks for in [loginId]'s mail: the login's own chosen addresses and every
 * other chosen recipient. When only the fields differ from the default, the login's own
 * addresses stand for the recipients. Null when neither is chosen.
 */
internal fun recipientAddresses(
    query: MailQuery,
    loginId: String,
): List<String>? {
    val filters = query.filters
    val chosen = filters.sentTo.filter { it.accountId == loginId }.map { it.address } + filters.to.sorted()
    return when {
        chosen.isNotEmpty() -> {
            chosen
        }

        filters.sentTo.isEmpty() && filters.receivedIn != RecipientField.Default -> {
            query.addresses[loginId].orEmpty().toList()
        }

        else -> {
            null
        }
    }
}

/** The fields recipients are looked up in; no field chosen reads as all. */
internal val InboxFilters.recipientFields: Set<RecipientField>
    get() = receivedIn.ifEmpty { RecipientField.entries.toSet() }

/** The mail in any of [mailboxIds] that meets every one of [conditions]. */
internal fun mailFilter(
    mailboxIds: Collection<String>,
    conditions: List<JsonObject>,
): JsonObject {
    val inMailboxes = anyOf(mailboxIds.map(::inMailbox))
    return if (conditions.isEmpty()) inMailboxes else operator("AND", listOf(inMailboxes) + conditions)
}

/** `Email/query` order by arrival for [sort]. */
internal fun receivedOrder(sort: FilterSort): List<JmapSort> =
    listOf(JmapSort("receivedAt", isAscending = sort == FilterSort.OldestFirst))

/** Whether the email has [name] at all: `header` with only a name (RFC 8621, section 4.4.1). */
private fun header(name: String): JsonObject = condition("header", JsonArray(listOf(JsonPrimitive(name))))

private fun keyword(
    name: String,
    keyword: String,
): JsonObject = condition(name, JsonPrimitive(keyword))

/** A `UTCDate` (RFC 8620, section 1.4): whole seconds and a `Z`. */
private fun Instant.utcDate(): String = Instant.fromEpochSeconds(epochSeconds).toString()

private const val SEEN = "\$seen"
private const val FLAGGED = "\$flagged"
private const val FORWARDED = "\$forwarded"

/** Headers lists and newsletters add (RFC 2919, RFC 2369); `header` with only a name asks whether it is there. */
private val LIST_HEADERS = listOf("List-Id", "List-Unsubscribe")
