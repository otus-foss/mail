package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.messages.domain.FilterSort
import kotlin.time.Instant

/** The rows one listing loaded so far, in its order, and whether the server has more. */
internal class ListingPage<T>(
    val rows: List<T>,
    val hasMore: Boolean,
)

/** Rows of several listings that can show together, and whether any listing has more. */
internal class MergedPages<T>(
    val rows: List<T>,
    val hasMore: Boolean,
)

/**
 * The rows of these listings that can show together in [sort] order. A listing with more rows on
 * the server stops at its last loaded row, and its next page may hold rows that sort before rows
 * other listings already loaded. So every row past the earliest such stop waits for a later page;
 * otherwise loading more would insert rows above ones already shown. Rows keep their listing order.
 */
internal fun <T> List<ListingPage<T>>.merged(
    sort: FilterSort,
    receivedAt: (T) -> Instant,
): MergedPages<T> {
    val stops = filter { it.hasMore }.mapNotNull { page -> page.rows.lastOrNull()?.let(receivedAt) }
    val cut = if (sort == FilterSort.OldestFirst) stops.minOrNull() else stops.maxOrNull()
    val shown =
        flatMap { it.rows }.filter { row ->
            val received = receivedAt(row)
            when {
                cut == null -> true
                sort == FilterSort.OldestFirst -> received <= cut
                else -> received >= cut
            }
        }
    return MergedPages(shown, hasMore = any { it.hasMore })
}
