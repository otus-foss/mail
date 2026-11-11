package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.shared.panels.components.PanelExpansion
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxState

/**
 * Closes [state]'s search and the keyboard, also on system back while searching, and turns off the
 * page filters the search's [changes] turned on.
 */
@Composable
private fun searchCloser(
    state: InboxState,
    filters: InboxFilterState,
    changes: SearchFilterChanges,
): () -> Unit {
    val focus = LocalFocusManager.current
    val closeSearch = {
        state.closeSearch()
        changes.undo(filters)
        focus.clearFocus()
    }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.searching,
        onBackCompleted = closeSearch,
    )
    return closeSearch
}

/** What the control panel's buttons do besides editing the search. */
internal class InboxHeaderActions(
    val closeSearch: () -> Unit,
    val onOpenNavigation: () -> Unit,
    val overlays: InboxOverlays,
    val expansion: PanelExpansion,
    val grown: @Composable () -> Unit,
    private val searchChanges: SearchFilterChanges,
) {
    /** Whether an overlay or the grown panel covers the list. */
    fun covers(state: InboxState): Boolean = overlays.cover(state) || expansion.expanded

    /** Whether a typed search fills the grown room, so the bar can show its results. */
    fun showsSearchBar(state: InboxState): Boolean = state.searchSurfaceOpen && state.search.query.isNotBlank()

    /** Whether the panel has grown to every filter, whose own Show button stands in for the action bar. */
    val showsFilters: Boolean get() = overlays.filters.isOpen

    /** Grows the panel to every filter. */
    fun openFilters() {
        expansion.expandTo(Int.MAX_VALUE)
    }

    /** Shows the mail the panel's filters pick and brings the panel back to its usual height. */
    fun showFiltered() {
        // The filters Show applies are the user's now, also those a search turned on.
        searchChanges.keep()
        expansion.collapse()
        overlays.filters.show()
    }
}

/**
 * The control panel's actions on [page], and the room it grows into: the quick view's modules
 * read [filters] and [messages] and open a file's mail by [rows].
 */
@Composable
internal fun rememberHeaderActions(
    state: InboxState,
    page: InboxContext,
    filters: InboxFilterState,
    messages: List<MailMessage>,
    rows: InboxRows,
    onOpenNavigation: () -> Unit,
    dateFormatter: DateFormatter,
): InboxHeaderActions {
    val focus = LocalFocusManager.current
    val changes = rememberSaveable(saver = SearchFilterChanges.saver) { SearchFilterChanges() }
    val closeSearch = searchCloser(state, filters, changes)
    val roomHeight = if (state.searchSurfaceOpen) searchRoomHeight(state.search.query.isNotBlank()) else null
    val typed = state.search.query.isNotBlank()
    val expansion = rememberInboxExpansion(page.quick, filters, roomHeight, typed) { leaveSearch(state, closeSearch) }
    // Results showing take the cursor from the field, so a tap on it is a fresh focus again.
    LaunchedEffect(state.resultsShown) { if (state.resultsShown) focus.clearFocus() }
    val overlays = rememberInboxOverlays(filters)
    val sources = QuickSources(filters, page, messages, rows.gestures.onOpen) { expansion.collapse() }
    val room =
        SearchRoom(state, sources, rows.gestures, dateFormatter, changes) {
            focus.clearFocus()
            expansion.expandTo(Int.MAX_VALUE)
        }
    return InboxHeaderActions(
        closeSearch,
        onOpenNavigation,
        overlays,
        expansion,
        grownPanel(expansion, sources, room, dateFormatter),
        changes,
    )
}

/** Shows the search's results once the room closes, or ends a search with nothing typed or picked. */
private fun leaveSearch(
    state: InboxState,
    closeSearch: () -> Unit,
) {
    // Where the terms match narrows nothing until there are terms.
    val picked = state.search.copy(field = state.defaultSearch.field) != state.defaultSearch
    if (state.search.query.isBlank() && !picked) closeSearch() else state.showResults()
}

/** What the panel grows: the quick view or search [room], then every filter. */
private fun grownPanel(
    expansion: PanelExpansion,
    sources: QuickSources,
    room: SearchRoom,
    dateFormatter: DateFormatter,
): @Composable () -> Unit = { ExpandedInboxContent(ExpandedInbox(expansion, sources, room, dateFormatter)) }
