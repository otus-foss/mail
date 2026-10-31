package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapResponse
import com.otus.jmap.QueryChangesMethod
import com.otus.jmap.QueryChangesResult
import com.otus.mail.core.mail.EmailType
import kotlinx.serialization.json.JsonPrimitive

/**
 * Each list of [pages] brought up to date. A [saved] listing with a query state follows the changes
 * since: `Email/queryChanges` (RFC 8620, section 5.6) up to its last row, spliced into its ids. The
 * others, and those whose changes the server could not say or that would leave fewer than
 * [minimum] rows while the server has more, load again with `Email/query`.
 */
internal suspend fun JmapConnection.refreshPages(
    accountId: String,
    pages: List<PageRequest>,
    saved: Map<String, Listing>,
    minimum: Int,
): List<Listing> {
    val followable =
        pages.filter { page -> saved[page.query.key]?.let { it.queryState != null && it.ids.isNotEmpty() } == true }
    val followed =
        followable
            .map { it.query.key }
            .zip(
                sendAll(
                    followable.map { page ->
                        val listing = saved.getValue(page.query.key)
                        CallGroup(size = 1) {
                            val changes = call(page.changesMethod(accountId, listing))
                            return@CallGroup { response: JmapResponse ->
                                unlessRefused { response[changes] }
                                    ?.let(listing::followed)
                                    ?.takeIf { it.ids.size >= minimum || !it.hasMore }
                            }
                        }
                    },
                ),
            ).toMap()
    val reloaded = pages.filter { followed[it.query.key] == null }
    val loaded = reloaded.map { it.query.key }.zip(queryPages(accountId, reloaded)).toMap()
    return pages.map { followed[it.query.key] ?: loaded.getValue(it.query.key) }
}

private fun PageRequest.changesMethod(
    accountId: String,
    listing: Listing,
) = QueryChangesMethod(
    EmailType,
    accountId,
    sinceQueryState = checkNotNull(listing.queryState),
    filter = query.filter,
    sort = receivedOrder(query.sort),
    maxChanges = MAX_LIST_CHANGES,
    extra =
        mapOf(
            "collapseThreads" to JsonPrimitive(true),
            "calculateTotal" to JsonPrimitive(true),
            "upToId" to JsonPrimitive(listing.ids.last()),
        ),
)

/**
 * The listing after [changes]: removed ids leave, then each added id goes in at its index, lowest
 * first. An id added past the rows loaded so far waits for the next page.
 */
private fun Listing.followed(changes: QueryChangesResult): Listing {
    val kept = ids - changes.removed.toSet()
    val spliced =
        changes.added.fold(kept) { rows, added ->
            val index = added.index.toInt()
            if (added.index > rows.size) rows else rows.take(index) + added.id + rows.drop(index)
        }
    return Listing(
        spliced.distinct(),
        hasMore = changes.total?.let { spliced.size < it } ?: hasMore,
        queryState = changes.newQueryState,
    )
}

/** More list changes than this cost more to read than the list itself. */
private const val MAX_LIST_CHANGES = 250
