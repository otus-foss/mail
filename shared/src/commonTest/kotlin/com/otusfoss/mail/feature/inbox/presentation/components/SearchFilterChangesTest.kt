package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.saveable.SaverScope
import com.otusfoss.mail.core.messages.domain.DateFilter
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Closing a search sets back only what it changed, to what the page had before it. */
class SearchFilterChangesTest {
    private val changes = SearchFilterChanges()

    /** Taps Has files in the search, as its chip does. */
    private fun InboxFilterState.tapFiles() {
        changes.files(applied)
        update(applied.with(QuickFacet.Attachments, !applied.has(QuickFacet.Attachments)))
    }

    /** Taps Past 30 days in the search, as its chip does. */
    private fun InboxFilterState.tapRecent() {
        changes.recent(applied)
        update(applied.copy(date = if (applied.date == DateFilter.Last30) DateFilter.Any else DateFilter.Last30))
    }

    @Test
    fun closingTurnsOffWhatTheSearchTurnedOn() {
        val filters = InboxFilterState()
        filters.tapFiles()
        filters.tapRecent()
        changes.undo(filters)
        assertFalse(filters.applied.has(QuickFacet.Attachments))
        assertEquals(DateFilter.Any, filters.applied.date)
    }

    @Test
    fun aFilterOnBeforeTheSearchStaysOnAfterBeingTappedOffAndOn() {
        val filters = InboxFilterState(InboxFilters().with(QuickFacet.Attachments, true).copy(date = DateFilter.Last30))
        filters.tapFiles()
        filters.tapFiles()
        filters.tapRecent()
        filters.tapRecent()
        changes.undo(filters)
        assertTrue(filters.applied.has(QuickFacet.Attachments))
        assertEquals(DateFilter.Last30, filters.applied.date)
    }

    @Test
    fun theDateGoesBackToTheOneTheSearchReplaced() {
        val filters = InboxFilterState(InboxFilters(date = DateFilter.Last7))
        filters.tapRecent()
        changes.undo(filters)
        assertEquals(DateFilter.Last7, filters.applied.date)
    }

    @Test
    fun keptChangesStayAfterTheSearchCloses() {
        val filters = InboxFilterState()
        filters.tapFiles()
        changes.keep()
        changes.undo(filters)
        assertTrue(filters.applied.has(QuickFacet.Attachments))
    }

    @Test
    fun changesSurviveBeingSavedAndRestored() {
        val filters = InboxFilterState(InboxFilters().with(QuickFacet.Attachments, true))
        filters.tapFiles()
        filters.tapRecent()
        val saved = with(SearchFilterChanges.saver) { SaverScope { true }.save(changes) }
        val restored = SearchFilterChanges.saver.restore(requireNotNull(saved))
        requireNotNull(restored).undo(filters)
        assertTrue(filters.applied.has(QuickFacet.Attachments))
        assertEquals(DateFilter.Any, filters.applied.date)
    }
}
