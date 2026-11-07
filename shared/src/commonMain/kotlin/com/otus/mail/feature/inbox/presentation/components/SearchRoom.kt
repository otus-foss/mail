package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.panels.components.GROWN_EXPANDED_STOP
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.theme.tokens.core.Sizes
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.ActionBarTokens
import com.otus.mail.feature.inbox.presentation.InboxState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * What the inbox panel's search room reads and does: [state]'s search over the mail [sources] hold,
 * narrowed by their filters. A hit opens through [gestures], and [onAllFilters] grows the room to
 * every filter.
 */
internal class SearchRoom(
    val state: InboxState,
    val sources: QuickSources,
    val gestures: RowGestures,
    val dateFormatter: DateFormatter,
    val changes: SearchFilterChanges,
    val onAllFilters: () -> Unit,
)

/**
 * The room the inbox panel grows for a search: a row of ways to narrow it, then, once a query is
 * typed, the people and mail that match, as the list shows them.
 */
@Composable
internal fun SearchRoomContent(room: SearchRoom) {
    // The captions below bring their own space, so the sections need no gap of their own.
    Column(Modifier.fillMaxSize()) {
        SearchNarrowRow(room)
        if (room.state.search.query
                .isNotBlank()
        ) {
            SearchMatches(room, Modifier.weight(1f))
        }
    }
}

/**
 * The search room's height: its row of choices and the grab handle until something is [typed], then
 * the window below the header row and above the keyboard, or the action bar while the keyboard is
 * down, less a gap.
 */
@Composable
internal fun searchRoomHeight(typed: Boolean): Dp {
    if (!typed) {
        return QuickViewTokens.moduleGap + Sizes.touchTarget + PanelTokens.handlePadding * 2 +
            PanelTokens.handleHeight
    }
    val window =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding() + ActionBarTokens.barInset
    // With the keyboard down, the action bar's place stays clear for the search's Show button.
    val bar =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            ActionBarTokens.height +
            ActionBarTokens.barInset * 2
    return window - top - PanelTokens.headerRowHeight - maxOf(keyboard, bar)
}

/**
 * Grows [expansion] into the search room while [searching], and brings the panel back once the
 * search leaves it. Pushing the room shut leaves the search through [onLeave].
 */
@Composable
internal fun FollowSearch(
    expansion: PanelExpansion,
    searching: Boolean,
    onLeave: () -> Unit,
) {
    val leave by rememberUpdatedState(onLeave)
    val inRoom by rememberUpdatedState(searching)
    LaunchedEffect(searching) {
        when {
            searching -> if (expansion.stop <= GROWN_EXPANDED_STOP) expansion.expandTo(GROWN_EXPANDED_STOP)
            expansion.expanded -> expansion.collapse()
        }
    }
    LaunchedEffect(expansion) {
        // Only a room that was open and shut leaves the search, not the first reading, such as when
        // a restored search composes before its room has grown.
        snapshotFlow { expansion.expanded }.distinctUntilChanged().drop(1).collect { open ->
            if (!open && inRoom) leave()
        }
    }
}
