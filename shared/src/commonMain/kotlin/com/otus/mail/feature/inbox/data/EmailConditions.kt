package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapSort
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.bounds
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant

/**
 * The `Email/query` conditions (RFC 8621, section 4.4.1) [query] puts on the mail of [loginId],
 * all of which must hold; null when the query reads none of that login's mail. Mailboxes are left
 * to the caller. Waiting for reply asks only for mail from the user's [MailQuery.ownAddresses].
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
        if (filters.has(ShowFacet.Unread)) add(condition("notKeyword", JsonPrimitive(SEEN)))
        if (filters.has(ShowFacet.Flagged)) add(condition("hasKeyword", JsonPrimitive(FLAGGED)))
        if (filters.has(ShowFacet.Attachments)) add(condition("hasAttachment", JsonPrimitive(true)))
        if (filters.has(ShowFacet.ReadLater)) add(inMailbox(query.readLaterIds.getValue(loginId)))
        addressCondition(query, loginId)?.let(::add)
        // Mail waiting for a reply is mail the user sent; the caller checks the rest.
        if (filters.has(
                ShowFacet.WaitingForReply,
            )
        ) {
            add(anyOf(query.ownAddresses.map { condition("from", JsonPrimitive(it)) }))
        }
        dates.after?.let { add(condition("after", JsonPrimitive(it.utcDate()))) }
        dates.before?.let { add(condition("before", JsonPrimitive(it.utcDate()))) }
    }
}

/** Mail sent to any selected address of [loginId], in To, Cc, or Bcc; null without a selection. */
private fun addressCondition(
    query: MailQuery,
    loginId: String,
): JsonObject? {
    val addresses =
        query.filters.sentTo
            .filter { it.accountId == loginId }
            .map { it.address }
    if (addresses.isEmpty()) return null
    return anyOf(addresses.flatMap { address -> ADDRESS_FIELDS.map { condition(it, JsonPrimitive(address)) } })
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

private fun condition(
    name: String,
    value: JsonPrimitive,
): JsonObject = buildJsonObject { put(name, value) }

/** A `UTCDate` (RFC 8620, section 1.4): whole seconds and a `Z`. */
private fun Instant.utcDate(): String = Instant.fromEpochSeconds(epochSeconds).toString()

private const val SEEN = "\$seen"
private const val FLAGGED = "\$flagged"
private val ADDRESS_FIELDS = listOf("to", "cc", "bcc")
