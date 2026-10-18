package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus

/**
 * The count line, any notice, and the matching messages, which respond to [gestures].
 * [padding] lets the last row scroll above an action bar.
 */
@Composable
internal fun InboxResults(
    messages: List<MailMessage>,
    status: InboxStatus,
    state: InboxState,
    filters: InboxFilters,
    dateFormatter: DateFormatter,
    gestures: RowGestures,
    padding: PaddingValues,
    emptyContent: @Composable () -> Unit,
) {
    val matching = state.matching
    val results = remember(messages, matching, filters) { searchMessages(messages, matching, filters) }
    val inboxScroll = rememberLazyListState()
    val searchScroll = rememberLazyListState()
    val listState = if (state.searching) searchScroll else inboxScroll
    LaunchedEffect(state.search, state.folderPill, filters) { listState.scrollToItem(0) }
    val noun = if (state.searching) "result" else "message"
    val countLabel =
        if (status.loading &&
            messages.isEmpty()
        ) {
            "Loading…"
        } else {
            "${results.size} $noun${if (results.size == 1) "" else "s"}"
        }
    Column {
        Text(
            countLabel,
            Modifier.padding(
                horizontal = InboxTokens.pageInset + InboxTokens.rowPadding,
                vertical = InboxTokens.rowPadding,
            ),
            color = OtusColors.text2,
            fontSize = InboxTokens.metadataSize,
        )
        status.notice?.takeIf { messages.isNotEmpty() }?.let {
            Text(
                it,
                Modifier.padding(horizontal = InboxTokens.pageInset + InboxTokens.rowPadding),
                color = OtusColors.text2,
                fontSize = InboxTokens.metadataSize,
            )
        }
        Box(Modifier.weight(1f)) {
            InboxBody(
                results,
                listState,
                dateFormatter,
                gestures,
                padding,
                emptyContent = emptyContent,
            )
        }
    }
}
