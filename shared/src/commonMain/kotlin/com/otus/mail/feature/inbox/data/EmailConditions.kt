package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapSort
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.bounds
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant

/**
 * The `Email/query` conditions (RFC 8621, section 4.4.1) [query] puts on the mail of [loginId],
 * all of which must hold; null when the query reads none of that login's mail. Mailboxes are left
 * to the caller. Waiting for reply asks only for mail from the login's own addresses, and calendar
 * invites have no condition; the caller checks both after loading.
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
        filters.show.sortedBy { it.ordinal }.mapNotNullTo(this) { it.condition(query, loginId) }
        if (filters.from.isNotEmpty()) add(anyOf(filters.from.map { condition("from", JsonPrimitive(it)) }))
        recipientCondition(query, loginId)?.let(::add)
        query.skipIds[loginId]?.takeIf { it.isNotEmpty() }?.let { ids -> add(operator("NOT", ids.map(::inMailbox))) }
        dates.after?.let { add(condition("after", JsonPrimitive(it.utcDate()))) }
        dates.before?.let { add(condition("before", JsonPrimitive(it.utcDate()))) }
        filters.size.minBytes?.let { add(condition("minSize", JsonPrimitive(it))) }
    }
}

/** This choice's condition for [loginId]; null for calendar invites, which the caller checks. */
private fun ShowFacet.condition(
    query: MailQuery,
    loginId: String,
): JsonObject? =
    when (this) {
        ShowFacet.Unread -> {
            keyword("notKeyword", SEEN)
        }

        ShowFacet.Read -> {
            keyword("hasKeyword", SEEN)
        }

        ShowFacet.Flagged -> {
            keyword("hasKeyword", FLAGGED)
        }

        ShowFacet.NotFlagged -> {
            keyword("notKeyword", FLAGGED)
        }

        ShowFacet.ReadLater -> {
            inMailbox(query.readLaterIds.getValue(loginId))
        }

        ShowFacet.Attachments -> {
            condition("hasAttachment", JsonPrimitive(true))
        }

        ShowFacet.Replied -> {
            keyword("hasKeyword", ANSWERED)
        }

        ShowFacet.NotReplied -> {
            keyword("notKeyword", ANSWERED)
        }

        ShowFacet.Forwarded -> {
            keyword("hasKeyword", FORWARDED)
        }

        // Mail waiting for a reply is mail the user sent; the caller checks the rest.
        ShowFacet.WaitingForReply -> {
            anyOf(
                query.addresses[loginId].orEmpty().map { condition("from", JsonPrimitive(it)) },
            )
        }

        ShowFacet.Newsletters -> {
            anyOf(LIST_HEADERS.map { condition("header", JsonArray(listOf(JsonPrimitive(it)))) })
        }

        ShowFacet.CalendarInvites -> {
            null
        }
    }

/**
 * Mail to the login's chosen addresses, or to all its own when only fields are chosen, in the
 * chosen fields or else To, Cc, and Bcc; null when neither is chosen.
 */
private fun recipientCondition(
    query: MailQuery,
    loginId: String,
): JsonObject? {
    val filters = query.filters
    if (filters.sentTo.isEmpty() && filters.receivedAs.isEmpty()) return null
    val chosen = filters.sentTo.filter { it.accountId == loginId }.map { it.address }
    val addresses = chosen.ifEmpty { query.addresses[loginId].orEmpty().toList() }
    val fields = filters.receivedAs.ifEmpty { RecipientField.entries.toSet() }.sortedBy { it.ordinal }
    return anyOf(addresses.flatMap { address -> fields.map { condition(it.jmapName, JsonPrimitive(address)) } })
}

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

/** One condition as itself; several under `OR`. */
private fun anyOf(conditions: List<JsonObject>): JsonObject = conditions.singleOrNull() ?: operator("OR", conditions)

private fun operator(
    name: String,
    conditions: List<JsonObject>,
): JsonObject =
    buildJsonObject {
        put("operator", JsonPrimitive(name))
        put("conditions", JsonArray(conditions))
    }

private fun keyword(
    name: String,
    keyword: String,
): JsonObject = condition(name, JsonPrimitive(keyword))

private fun condition(
    name: String,
    value: JsonElement,
): JsonObject = buildJsonObject { put(name, value) }

/** A `UTCDate` (RFC 8620, section 1.4): whole seconds and a `Z`. */
private fun Instant.utcDate(): String = Instant.fromEpochSeconds(epochSeconds).toString()

private const val SEEN = "\$seen"
private const val FLAGGED = "\$flagged"
private const val ANSWERED = "\$answered"
private const val FORWARDED = "\$forwarded"

/** Headers lists and newsletters add (RFC 2919, RFC 2369); `header` with only a name asks whether it is there. */
private val LIST_HEADERS = listOf("List-Id", "List-Unsubscribe")
