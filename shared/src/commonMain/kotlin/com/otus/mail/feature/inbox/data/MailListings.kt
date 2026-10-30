package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapJson
import com.otus.jmap.JmapResponse
import com.otus.jmap.QueryMethod
import com.otus.jmap.QueryResult
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailType
import com.otus.mail.feature.inbox.domain.FilterSort
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One mailbox's list: the mail in [mailboxId] that [filter] finds, in [sort] order. */
internal class ListingQuery(
    val mailboxId: String,
    val filter: JsonObject,
    val sort: FilterSort,
) {
    /** The saved-value key of this list's [Listing]; the filter names the mailbox. */
    val key: String
        get() = "$LISTING_PREFIX${sort.name}:$filter"
}

/** The row email ids of a list loaded so far, in its order, and whether the server has more. */
@Serializable
internal data class Listing(
    val ids: List<String> = emptyList(),
    val hasMore: Boolean = false,
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

/** One page of a list to ask for: [limit] rows from [position]. */
internal class PageRequest(
    val query: ListingQuery,
    val position: Int,
    val limit: Int,
)

/**
 * The row ids of each page in [pages], one `Email/query` per page with `collapseThreads`, so each
 * conversation has one row (RFC 8621, section 4.4.3). The pages share as few requests as the
 * server allows.
 */
internal suspend fun JmapConnection.queryPages(
    accountId: String,
    pages: List<PageRequest>,
): List<Listing> =
    sendAll(
        pages.map { page ->
            CallGroup(size = 1) {
                val query = call(page.queryMethod(accountId))
                return@CallGroup { response: JmapResponse -> response[query].toListing(page.limit) }
            }
        },
    )

private fun PageRequest.queryMethod(accountId: String) =
    QueryMethod(
        EmailType,
        accountId,
        query.filter,
        receivedOrder(query.sort),
        limit,
        mapOf(
            "collapseThreads" to JsonPrimitive(true),
            "calculateTotal" to JsonPrimitive(true),
            "position" to JsonPrimitive(position),
        ),
    )

/** More remain when the total says so, or, without a total, when the page came back full. */
private fun QueryResult.toListing(limit: Int): Listing =
    Listing(ids, hasMore = total?.let { position + ids.size < it } ?: (ids.size >= limit))

private fun <T> String.decodedOrNull(serializer: KSerializer<T>): T? =
    try {
        JmapJson.decodeFromString(serializer, this)
    } catch (expected: SerializationException) {
        null
    } catch (expected: IllegalArgumentException) {
        null
    }

private const val LISTING_PREFIX = "listing:"
private const val LISTING_USE = "listings:used"
private val USE_SERIALIZER = MapSerializer(String.serializer(), Long.serializer())

/** Listings kept per login: enough for every page and folder in use, with room for some filters. */
private const val MAX_LISTINGS = 40
