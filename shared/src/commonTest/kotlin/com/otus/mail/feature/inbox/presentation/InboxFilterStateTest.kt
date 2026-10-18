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
import com.otus.mail.feature.inbox.domain.QuickFacet
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
    fun pagesOpenOnTopOfTheMainPageAndBackReturnsToIt() {
        val state = InboxFilterState()
        state.openPage(FilterPage.Folders)
        assertEquals(FilterPage.Main, state.page, "A closed panel opens no page")
        state.open()
        assertEquals(FilterPage.Main, state.page)
        state.openPage(FilterPage.From)
        assertEquals(FilterPage.From, state.page)
        state.back()
        assertEquals(FilterPage.Main, state.page)
        assertTrue(state.isOpen, "Back on a page keeps the panel open")
        state.edit { unread }
        state.back()
        assertFalse(state.isOpen, "Back on the main page closes the panel")
        assertEquals(InboxFilters(), state.applied, "and drops the draft")
    }

    @Test
    fun everyPageEditsTheSameDraft() {
        val state = InboxFilterState()
        state.open()
        state.openPage(FilterPage.Date)
        state.edit { it.copy(date = DateFilter.ThisYear) }
        state.back()
        state.openPage(FilterPage.Metadata)
        state.edit { it.with(QuickFacet.Flagged, true) }
        assertEquals(InboxFilters(date = DateFilter.ThisYear, quick = setOf(QuickFacet.Flagged)), state.draft)
        state.show()
        assertEquals(DateFilter.ThisYear, state.applied.date)
    }

    @Test
    fun resetOnAPageClearsTheDraftAndStaysThere() {
        val state = InboxFilterState(unread.copy(date = DateFilter.Today))
        state.open()
        state.openPage(FilterPage.To)
        state.reset()
        assertEquals(InboxFilters(), state.draft)
        assertEquals(FilterPage.To, state.page)
        state.close()
        state.open()
        assertEquals(FilterPage.Main, state.page, "Reopening starts on the main page")
    }

    @Test
    fun openingStartsACountingSession() {
        var sessions = 0
        val state = InboxFilterState(onOpen = { sessions++ })
        state.open()
        state.edit { unread }
        assertEquals(1, sessions, "Draft changes count within the same session")
        state.close()
        state.open()
        assertEquals(2, sessions)
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
        assertEquals("Show conversations", FilterCount.Counting.showLabel)
        assertEquals("Show conversations", FilterCount.Unknown.showLabel)
        assertEquals("No conversations", FilterCount.Known(0).showLabel)
        assertEquals("Show 1 conversation", FilterCount.Known(1).showLabel)
        assertEquals("Show 12 conversations", FilterCount.Known(12).showLabel)
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
