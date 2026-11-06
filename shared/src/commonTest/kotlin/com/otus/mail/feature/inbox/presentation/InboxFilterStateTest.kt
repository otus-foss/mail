package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.PeopleField
import com.otus.mail.feature.inbox.domain.ReadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFilterStateTest {
    private val unread = InboxFilters(status = ReadStatus.Unread)

    @Test
    fun openingCopiesTheAppliedFiltersIntoTheDraft() {
        val state = InboxFilterState(unread)
        state.open()
        assertEquals(unread, state.draft)
        state.edit { it.copy(date = DateFilter.Today) }
        assertEquals(unread, state.applied, "Draft changes wait for Show")
    }

    @Test
    fun closingDropsTheDraft() {
        val applied = mutableListOf<InboxFilters>()
        val state = InboxFilterState(onApplied = { applied += it })
        state.open()
        state.edit { unread }
        state.close()
        assertNull(state.draft)
        assertFalse(state.isOpen)
        assertEquals(InboxFilters(), state.applied)
        assertTrue(applied.isEmpty())
        state.open()
        assertEquals(InboxFilters(), state.draft, "Reopening starts from the applied filters")
    }

    @Test
    fun showAppliesTheDraftAndCloses() {
        val applied = mutableListOf<InboxFilters>()
        val state = InboxFilterState(onApplied = { applied += it })
        state.open()
        state.edit { unread }
        state.show()
        assertEquals(unread, state.applied)
        assertEquals(listOf(unread), applied)
        assertFalse(state.isOpen)
    }

    @Test
    fun pillsAndPanelShareTheAppliedFilters() {
        val state = InboxFilterState()
        state.update(state.applied.with(InboxPill.Filter.Unread, true))
        state.open()
        assertEquals(ReadStatus.Unread, state.draft!!.status, "The panel opens with the pill's filter on")
        state.edit { it.copy(status = ReadStatus.All) }
        state.show()
        assertFalse(state.applied.has(InboxPill.Filter.Unread), "The pill turns off with the panel's change")
    }

    @Test
    fun resetClearsTheDraftAndKeepsThePanelOpen() {
        val state = InboxFilterState(unread.copy(date = DateFilter.Today))
        state.open()
        state.reset()
        assertEquals(InboxFilters(), state.draft)
        assertTrue(state.isOpen)
        state.close()
        assertEquals(DateFilter.Today, state.applied.date, "Closing drops the reset draft")
    }

    @Test
    fun peopleSearchWaitsForTheTextAndClearsWithThePanel() =
        runTest {
            val asked = mutableListOf<Pair<PeopleField, String>>()
            val grace = FoundPerson(MailSender("Grace", "grace@x.org"), 3)
            val state =
                InboxFilterState(scope = this, findPeople = { field, text ->
                    asked += field to text
                    listOf(grace)
                })
            state.open()
            state.senders.search("gr")
            state.senders.search("grace ")
            advanceTimeBy(FilterPeopleSearch.SEARCH_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(PeopleField.From to "grace"), asked)
            assertEquals(listOf(grace), state.senders.found)
            state.close()
            assertEquals("", state.senders.text)
            assertTrue(state.senders.found.isEmpty())
        }

    @Test
    fun countingWaitsForTheLastChangeAndCancelsTheOneBefore() =
        runTest {
            val counted = mutableListOf<InboxFilters>()
            var blocking = true
            val state =
                InboxFilterState(scope = this, counter = { filters ->
                    counted += filters
                    if (blocking) awaitCancellation()
                    7
                })
            state.open()
            advanceTimeBy(InboxFilterState.COUNT_DELAY_MILLIS + 1)
            assertEquals(FilterCount.Counting, state.count)
            blocking = false
            state.edit { unread }
            advanceTimeBy(InboxFilterState.COUNT_DELAY_MILLIS / 2)
            state.edit { it.copy(date = DateFilter.Today) }
            advanceTimeBy(InboxFilterState.COUNT_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(FilterCount.Known(7), state.count)
            assertEquals(listOf(InboxFilters(), unread.copy(date = DateFilter.Today)), counted)
        }

    @Test
    fun showLabelsFollowTheCount() {
        assertEquals("Show mails", FilterCount.Counting.showLabel)
        assertEquals("Show mails", FilterCount.Unknown.showLabel)
        assertEquals("No mails", FilterCount.Known(0).showLabel)
        assertEquals("Show 1 mail", FilterCount.Known(1).showLabel)
        assertEquals("Show 12 mails", FilterCount.Known(12).showLabel)
    }

    @Test
    fun aNewQueryLoadsTheFeedAgain() =
        runTest {
            val queries = mutableListOf<MailQuery>()
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) = InboxSnapshot(emptyList())

                    override suspend fun messages(
                        scope: MailScope,
                        query: MailQuery,
                    ): InboxSnapshot {
                        queries += query
                        return InboxSnapshot(emptyList())
                    }
                }
            val feed = InboxFeedState(source, this)
            feed.refresh()
            runCurrent()
            feed.query = MailQuery(unread)
            runCurrent()
            feed.query = MailQuery(unread)
            runCurrent()
            assertEquals(listOf(MailQuery(), MailQuery(unread)), queries)
        }
}
