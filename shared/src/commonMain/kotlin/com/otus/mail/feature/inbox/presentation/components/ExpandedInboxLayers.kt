package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Whether the filters show: while their draft is open, and on until the panel stops moving after,
 * so a drag that started in them keeps its list under the finger and ends where the panel settles.
 */
@Composable
internal fun rememberFiltersShown(inbox: ExpandedInbox): Boolean {
    val open = inbox.sources.filters.isOpen
    var shown by remember { mutableStateOf(open) }
    if (open) {
        shown = true
    } else if (!inbox.expansion.moving) {
        shown = false
    }
    return shown
}

/** Whether the search room shows: while the search is in it, and until the panel has closed after. */
@Composable
internal fun rememberSearchShown(inbox: ExpandedInbox): Boolean {
    val searching = inbox.room.state.searchSurfaceOpen
    val expanded by remember(inbox.expansion) { derivedStateOf { inbox.expansion.expanded } }
    var shown by remember { mutableStateOf(searching) }
    if (searching) {
        shown = true
    } else if (!expanded) {
        shown = false
    }
    return shown
}

/** Leaves the accessibility tree while [hidden], such as a layer faded out. */
internal fun Modifier.hiddenWhen(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics {} else this
