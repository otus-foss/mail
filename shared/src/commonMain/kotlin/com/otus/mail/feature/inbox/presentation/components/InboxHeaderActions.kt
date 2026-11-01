package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState

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
    BackHandler(state.searching, closeSearch)
    return closeSearch
}

/** What the control panel's buttons do besides editing the search. */
internal class InboxHeaderActions(
    val closeSearch: () -> Unit,
    val onOpenNavigation: () -> Unit,
    val overlays: InboxOverlays,
    val expansion: PanelExpansion? = null,
    val grown: @Composable () -> Unit = {},
    private val searchChanges: SearchFilterChanges? = null,
) {
    /** Whether an overlay or the grown panel covers the list. */
    fun covers(state: InboxState): Boolean = overlays.cover(state) || expansion?.expanded == true

    /** Whether the panel shows the search field: while searching if it grows a search room, else with results. */
    fun searching(state: InboxState): Boolean = if (expansion != null) state.searching else state.resultsShown

    /** Whether the search field takes the keyboard: while the search room is open, else with results. */
    fun searchFocused(state: InboxState): Boolean =
        if (expansion !=
            null
        ) {
            state.searchSurfaceOpen
        } else {
            state.resultsShown
        }

    /** Whether a typed search fills the grown room, so the bar can show its results. */
    fun showsSearchBar(state: InboxState): Boolean =
        expansion != null && state.searchSurfaceOpen && state.search.query.isNotBlank()

    /** Whether the panel has grown to every filter, whose own Show button stands in for the action bar. */
    val showsFilters: Boolean get() = overlays.filtersInPanel && overlays.filters.isOpen

    /** Every filter: grown inside the panel when it can grow, else the filter panel's own sheet. */
    fun openFilters() {
        expansion?.expandTo(Int.MAX_VALUE) ?: overlays.filters.open()
    }

    /** Shows the mail the panel's filters pick and brings the panel back to its usual height. */
    fun showFiltered() {
        // The filters Show applies are the user's now, also those a search turned on.
        searchChanges?.keep()
        expansion?.collapse()
        overlays.filters.show()
    }
}

/**
 * The control panel's actions on [page], and the room it grows into while the page has a quick
 * view: the quick view's modules read [filters] and [messages] and open a file's mail by [rows].
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
    val changes = remember { SearchFilterChanges() }
    val closeSearch = searchCloser(state, filters, changes)
    // Only a page with a quick view grows a search room; others keep their search sheet.
    val roomHeight =
        if (state.searchSurfaceOpen && page.quick != null) searchRoomHeight(state.search.query.isNotBlank()) else null
    val typed = state.search.query.isNotBlank()
    val expansion = rememberInboxExpansion(page.quick, filters, roomHeight, typed) { leaveSearch(state, closeSearch) }
    // With a search room, results showing take the cursor from the field, so a tap on it is a fresh
    // focus again. Without one, the results keep the field focused.
    LaunchedEffect(state.resultsShown) { if (state.resultsShown && expansion != null) focus.clearFocus() }
    val overlays = rememberInboxOverlays(filters, filtersInPanel = expansion != null)
    val sources = QuickSources(filters, page, messages, rows.gestures.onOpen) { expansion?.collapse() }
    val room =
        SearchRoom(state, sources, rows.gestures, dateFormatter, changes) {
            focus.clearFocus()
            expansion?.expandTo(Int.MAX_VALUE)
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
    val search = state.search
    if (search.query.isBlank() && search.senders.isEmpty()) closeSearch() else state.showResults()
}

/** What the panel grows, while it has an [expansion]: the quick view or search [room], then every filter. */
private fun grownPanel(
    expansion: PanelExpansion?,
    sources: QuickSources,
    room: SearchRoom,
    dateFormatter: DateFormatter,
): @Composable () -> Unit = { expansion?.let { ExpandedInboxContent(ExpandedInbox(it, sources, room, dateFormatter)) } }
