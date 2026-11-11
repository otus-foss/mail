package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxState

/**
 * What can cover the inbox besides an open message: the page's [filters], grown inside the
 * control panel, and the account switcher while [accountsOpen].
 */
internal class InboxOverlays(
    val filters: InboxFilterState,
    accountsOpen: MutableState<Boolean>,
) {
    var accountsOpen by accountsOpen

    /** Whether one of them, or [state]'s search surface, covers the list. */
    fun cover(state: InboxState): Boolean = filters.isOpen || accountsOpen || state.searchSurfaceOpen

    /** Whether one of them covers the whole page, the control panel and action bar too. */
    fun coverPage(): Boolean = accountsOpen
}

@Composable
internal fun rememberInboxOverlays(filters: InboxFilterState): InboxOverlays =
    InboxOverlays(filters, rememberSaveable { mutableStateOf(false) })
