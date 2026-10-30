package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Reports true through [onCompactChange] once the user scrolls down with the first row out of view,
 * and false as soon as they scroll back up, like a header that gives the list room while reading
 * down. Only the user's scrolling counts: a jump back to the top, such as for a new filter, keeps
 * the panel as it is.
 */
@Composable
internal fun rememberCompactOnScroll(
    listState: LazyListState,
    onCompactChange: (Boolean) -> Unit,
): NestedScrollConnection {
    val report by rememberUpdatedState(onCompactChange)
    return remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when {
                    available.y < 0 && listState.firstVisibleItemIndex > 0 -> report(true)
                    available.y > 0 -> report(false)
                }
                return Offset.Zero
            }
        }
    }
}
