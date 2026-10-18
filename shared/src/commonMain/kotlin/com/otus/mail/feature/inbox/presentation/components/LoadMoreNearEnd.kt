package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** How many rows before the end of the list the next page starts loading, so it is there in time. */
internal const val LOAD_MORE_ROWS_AHEAD = 10

/**
 * Calls [onLoadMore] when the last visible row of [listState] comes within [LOAD_MORE_ROWS_AHEAD]
 * rows of the end while [canLoad]. It watches the layout rather than recompositions, and calls once
 * per list length, so a list that stays short after a page does not load again until it grows or
 * a load ends.
 */
@Composable
internal fun LoadMoreNearEnd(
    listState: LazyListState,
    canLoad: Boolean,
    onLoadMore: () -> Unit,
) {
    val loadable by rememberUpdatedState(canLoad)
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.takeIf { loadable && it.nearEnd() }?.totalItemsCount }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { load() }
    }
}

private fun LazyListLayoutInfo.nearEnd(): Boolean {
    val last = visibleItemsInfo.lastOrNull() ?: return false
    return last.index >= totalItemsCount - 1 - LOAD_MORE_ROWS_AHEAD
}
