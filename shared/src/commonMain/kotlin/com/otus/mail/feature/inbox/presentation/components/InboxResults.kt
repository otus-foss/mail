package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.ListFrame

/**
 * Any notice and the matching messages, which respond to [gestures]. The list runs under the
 * control panel, so mail shows behind the pills; [frame] places it on the page.
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
    LaunchedEffect(state.search, state.folderPill, filters) { listState.scrollToItem(0) }
    Box(Modifier.fillMaxSize().nestedScroll(rememberCompactOnScroll(listState, frame.onCompactChange))) {
        InboxBody(
            results,
            listState,
            dateFormatter,
            gestures,
            frame,
            status.notice?.takeIf { messages.isNotEmpty() },
            emptyContent = emptyContent,
        )
    }
}
