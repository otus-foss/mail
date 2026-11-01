package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.otus.dateformat.DateFormatter
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.ListFrame
import kotlinx.coroutines.flow.drop

/**
 * Any notice and the matching messages, which respond to [gestures]. The list runs under the
 * control panel, so mail shows behind the pills; [frame] places it on the page. Scrolling near the
 * end loads the next page while the feed has more.
 */
@Composable
internal fun InboxResults(
    listed: ListedMail,
    status: InboxStatus,
    state: InboxState,
    dateFormatter: DateFormatter,
    gestures: RowGestures,
    frame: ListFrame,
    emptyContent: @Composable () -> Unit,
) {
    val messages = listed.messages
    val filters = listed.filters
    val matching = state.matching
    val results = remember(messages, matching, filters) { searchMessages(messages, matching, filters) }
    val inboxScroll = rememberLazyListState()
    val searchScroll = rememberLazyListState()
    val listState = if (state.searching) searchScroll else inboxScroll
    TopForNewResults(listState, Triple(state.search, state.folderPill, filters))
    StayAtTop(listState, results.firstOrNull()?.id)
    LoadMoreNearEnd(listState, status.hasMore && !status.loading && !status.loadingMore, frame.onLoadMore)
    Box(Modifier.fillMaxSize().nestedScroll(rememberCompactOnScroll(listState, frame.onCompactChange))) {
        InboxBody(
            results,
            listState,
            dateFormatter,
            gestures,
            frame.copy(narrowed = filters.activeCount > 0),
            status,
            emptyContent = emptyContent,
        )
    }
}

/**
 * Scrolls [listState] to the top when [shown], what the list shows, changes. A page that comes back
 * keeps its saved position, so composing it again does not scroll.
 */
@Composable
private fun TopForNewResults(
    listState: LazyListState,
    shown: Any,
) {
    val currentList by rememberUpdatedState(listState)
    val currentShown by rememberUpdatedState(shown)
    LaunchedEffect(Unit) {
        snapshotFlow { currentShown }
            .drop(1)
            .collect { currentList.scrollToItem(0) }
    }
}

/**
 * Shows mail that arrives above the first row when the list was at the top. A lazy list keeps the
 * row in view in place, which would leave the new mail above the screen.
 */
@Composable
private fun StayAtTop(
    listState: LazyListState,
    firstId: String?,
) {
    // Read while composing the new first row, before the list measures it and moves down; reading
    // without observation keeps scrolling from recomposing this.
    val wasAtTop =
        remember(firstId) {
            Snapshot.withoutReadObservation {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }
        }
    LaunchedEffect(firstId) { if (wasAtTop) listState.scrollToItem(0) }
}
