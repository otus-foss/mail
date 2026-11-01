package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState

/**
 * What can cover the inbox besides an open message: the page's [filters] panel, the account
 * switcher while [accountsOpen], the quick view while [quickOpen], and a search filter's sheet
 * while a [filter] is chosen.
 */
internal class InboxOverlays(
    val filters: InboxFilterState,
    accountsOpen: MutableState<Boolean>,
    filter: MutableState<InboxFilter?>,
    quickOpen: MutableState<Boolean>,
) {
    var accountsOpen by accountsOpen
    var filter by filter
    var quickOpen by quickOpen

    /** Closes the quick view for every filter: the panel's second pull. */
    fun allFilters() {
        quickOpen = false
        filters.open()
    }

    /** Whether one of them, or [state]'s search surface, covers the list. */
    fun cover(state: InboxState): Boolean = filters.isOpen || accountsOpen || quickOpen || state.searchSurfaceOpen
}

@Composable
internal fun rememberInboxOverlays(filters: InboxFilterState): InboxOverlays =
    InboxOverlays(
        filters,
        rememberSaveable { mutableStateOf(false) },
        rememberSaveable { mutableStateOf<InboxFilter?>(null) },
        rememberSaveable { mutableStateOf(false) },
    )
