package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.messages.domain.FilterSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ListingMergeTest {
    private class Row(
        val id: String,
        val at: Instant,
    )

    private fun row(
        id: String,
        hour: Int,
    ) = Row(id, Instant.parse("2026-10-18T${hour.toString().padStart(2, '0')}:00:00Z"))

    private fun List<ListingPage<Row>>.ids(sort: FilterSort = FilterSort.NewestFirst) =
        merged(sort) { it.at }.rows.map { it.id }

    @Test
    fun rowsPastTheEarliestStopOfAListWithMoreWait() {
        // Home has older mail on the server; Work loaded all of its mail.
        val home = ListingPage(listOf(row("h1", 10), row("h2", 8)), hasMore = true)
        val work = ListingPage(listOf(row("w1", 9), row("w2", 5)), hasMore = false)
        val merged = listOf(home, work).merged(FilterSort.NewestFirst) { it.at }
        assertEquals(listOf("h1", "h2", "w1"), merged.rows.map { it.id })
        assertTrue(merged.hasMore)
    }

    @Test
    fun theListThatStopsFirstSetsTheCut() {
        val home = ListingPage(listOf(row("h1", 10), row("h2", 8)), hasMore = true)
        val work = ListingPage(listOf(row("w1", 9), row("w2", 7), row("w3", 3)), hasMore = true)
        assertEquals(listOf("h1", "h2", "w1"), listOf(home, work).ids())
    }

    @Test
    fun oldestFirstCutsAtTheEarliestNewestRow() {
        val home = ListingPage(listOf(row("h1", 2), row("h2", 4)), hasMore = true)
        val work = ListingPage(listOf(row("w1", 3), row("w2", 6)), hasMore = false)
        assertEquals(listOf("h1", "h2", "w1"), listOf(home, work).ids(FilterSort.OldestFirst))
    }

    @Test
    fun completeListsShowEveryRow() {
        val home = ListingPage(listOf(row("h1", 10)), hasMore = false)
        val work = ListingPage(listOf(row("w1", 1)), hasMore = false)
        val merged = listOf(home, work).merged(FilterSort.NewestFirst) { it.at }
        assertEquals(listOf("h1", "w1"), merged.rows.map { it.id })
        assertFalse(merged.hasMore)
    }
}
