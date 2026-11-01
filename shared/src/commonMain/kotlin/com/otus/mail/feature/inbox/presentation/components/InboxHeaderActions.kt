package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState

/** Closes [state]'s search and the keyboard, also on system back while searching. */
@Composable
private fun searchCloser(state: InboxState): () -> Unit {
    val focus = LocalFocusManager.current
    val closeSearch = {
        state.closeSearch()
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
) {
    /** Whether an overlay or the grown panel covers the list. */
    fun covers(state: InboxState): Boolean = overlays.cover(state) || expansion?.expanded == true

    /** Whether the panel has grown to every filter, whose own Show button stands in for the action bar. */
    val showsFilters: Boolean get() = overlays.filtersInPanel && overlays.filters.isOpen

    /** Every filter: grown inside the panel when it can grow, else the filter panel's own sheet. */
    fun openFilters() {
        expansion?.expandTo(Int.MAX_VALUE) ?: overlays.filters.open()
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
    val expansion = rememberInboxExpansion(page.quick, filters)
    val overlays = rememberInboxOverlays(filters, filtersInPanel = expansion != null)
    val sources = QuickSources(filters, page, messages, rows.gestures.onOpen) { expansion?.collapse() }
    return InboxHeaderActions(
        searchCloser(state),
        onOpenNavigation,
        overlays,
        expansion,
        grownPanel(expansion, sources, dateFormatter),
    )
}

/** What the panel shows as it grows, while it has an [expansion]: the quick view, then every filter. */
private fun grownPanel(
    expansion: PanelExpansion?,
    sources: QuickSources,
    dateFormatter: DateFormatter,
): @Composable () -> Unit = { expansion?.let { ExpandedInboxContent(ExpandedInbox(it, sources, dateFormatter)) } }
