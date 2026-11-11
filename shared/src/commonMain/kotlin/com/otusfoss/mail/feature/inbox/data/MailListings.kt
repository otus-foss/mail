package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapFilter
import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.QueryResult
import com.otusfoss.mail.core.cache.CacheWrite
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.unlessRefused
import com.otusfoss.mail.core.messages.data.CallGroup
import com.otusfoss.mail.core.messages.data.sendAll
import com.otusfoss.mail.core.messages.domain.FilterSort
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive

/**
 * One mailbox's list: the mail in [mailboxId] that [filter] finds, in [sort] order, with one row per
 * conversation, or per email when [conversations] is false (`collapseThreads`, RFC 8621, section
 * 4.4.3).
 */
internal class ListingQuery(
    val mailboxId: String,
    val filter: JmapFilter,
    val sort: FilterSort,
    val conversations: Boolean = true,
) {
    /** The saved-value key of this list's [Listing]; the filter names the mailbox. */
    val key: String
        get() = "$LISTING_PREFIX${sort.name}:${if (conversations) "" else EMAILS}${filter.toJson()}"

    /** The `Email/query` arguments that make rows conversations or emails. */
    val collapse: Pair<String, JsonPrimitive>
        get() = "collapseThreads" to JsonPrimitive(conversations)
}

/**
 * The row email ids of a list loaded so far, in its order, and whether the server has more.
 * [queryState] is the `Email/query` state the ids match, kept only when the server can say what
 * changed since (RFC 8620, section 5.6).
 */
@Serializable
internal data class Listing(
    val ids: List<String> = emptyList(),
    val hasMore: Boolean = false,
    val queryState: String? = null,
)

/** The saved listings of [queries], by key. A listing that no longer decodes counts as missing. */
internal suspend fun MailCache.listings(
    loginId: String,
    queries: List<ListingQuery>,
): Map<String, Listing> =
    queries
        .mapNotNull { query -> value(loginId, query.key)?.decodedOrNull(Listing.serializer())?.let { query.key to it } }
        .toMap()

/** When each saved listing of [loginId] was last loaded, in epoch milliseconds, by key. */
internal suspend fun MailCache.listingUse(loginId: String): Map<String, Long> =
    value(loginId, LISTING_USE)?.decodedOrNull(USE_SERIALIZER).orEmpty()

/**
 * Saves [listings], loaded at [now], with [use] from [listingUse]. Only the [MAX_LISTINGS] most
 * recently loaded listings stay, so filters tried once do not keep their ids forever; the ones
 * saved now always stay.
 */
internal fun CacheWrite.putListings(
    listings: Map<String, Listing>,
    use: Map<String, Long>,
    now: Long,
) {
    val others = use.filterKeys { it !in listings }
    val kept =
        others.entries
            .sortedByDescending { it.value }
            .take((MAX_LISTINGS - listings.size).coerceAtLeast(0))
            .associate { it.key to it.value } + listings.keys.associateWith { now }
    (others.keys - kept.keys).forEach { putValue(it, null) }
    listings.forEach { (key, listing) -> putValue(key, JmapJson.encodeToString(Listing.serializer(), listing)) }
    putValue(LISTING_USE, JmapJson.encodeToString(USE_SERIALIZER, kept))
}

/**
 * One page of a list to ask for: [limit] rows from [position], or, with [anchor], the [limit] rows
 * right after the row [anchor] (`anchorOffset` 1, RFC 8620, section 5.5), which stays right when
 * rows before it left the list since it loaded.
 */
internal class PageRequest(
    val query: ListingQuery,
    val position: Int = 0,
    val limit: Int,
    val anchor: String? = null,
)

/**
 * The row ids of each page in [pages], one `Email/query` per page, with a row per conversation or
 * per email as its [ListingQuery] says. The pages share as few requests as the
 * server allows.
 */
internal suspend fun JmapConnection.queryPages(
    accountId: String,
    pages: List<PageRequest>,
): List<Listing> = sendAll(pages.map { page -> queryGroup(accountId, page) { it() } })

/**
 * [queryPages], but a page the server refuses, such as with `anchorNotFound` when its anchor left
 * the list, comes back null.
 */
internal suspend fun JmapConnection.queryPagesOrNull(
    accountId: String,
    pages: List<PageRequest>,
): List<Listing?> = sendAll(pages.map { page -> queryGroup(accountId, page) { unlessRefused(it) } })

private fun <T> queryGroup(
    accountId: String,
    page: PageRequest,
    read: (() -> Listing) -> T,
) = CallGroup(size = 1) {
    val query = call(page.queryMethod(accountId))
    return@CallGroup { response: JmapResponse -> read { response[query].toListing(page.limit) } }
}

private fun PageRequest.queryMethod(accountId: String) =
    QueryMethod(
        EmailType,
        accountId,
        query.filter,
        receivedOrder(query.sort),
        limit,
        mapOf(
            query.collapse,
            "calculateTotal" to JsonPrimitive(true),
        ) + start(),
    )

private fun PageRequest.start(): Map<String, JsonPrimitive> =
    if (anchor == null) {
        mapOf("position" to JsonPrimitive(position))
    } else {
        mapOf("anchor" to JsonPrimitive(anchor), "anchorOffset" to JsonPrimitive(1))
    }

/** More remain when the total says so, or, without a total, when the page came back full. */
private fun QueryResult.toListing(limit: Int): Listing =
    Listing(
        ids,
        hasMore = total?.let { position + ids.size < it } ?: (ids.size >= limit),
        queryState = queryState.takeIf { canCalculateChanges && it.isNotEmpty() },
    )

private fun <T> String.decodedOrNull(serializer: KSerializer<T>): T? =
    try {
        JmapJson.decodeFromString(serializer, this)
    } catch (expected: SerializationException) {
        null
    } catch (expected: IllegalArgumentException) {
        null
    }

private const val LISTING_PREFIX = "listing:"
private const val EMAILS = "emails:"
private const val LISTING_USE = "listings:used"
private val USE_SERIALIZER = MapSerializer(String.serializer(), Long.serializer())

/** Listings kept per login: enough for every page and folder in use, with room for some filters. */
private const val MAX_LISTINGS = 40
