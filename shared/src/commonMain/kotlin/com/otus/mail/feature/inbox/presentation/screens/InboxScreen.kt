package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.designsystem.components.PullToRefresh
import com.otus.mail.designsystem.theme.InboxTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.domain.selectedSenders
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.InboxBody
import com.otus.mail.feature.inbox.presentation.components.InboxEmptyState
import com.otus.mail.feature.inbox.presentation.components.InboxFilter
import com.otus.mail.feature.inbox.presentation.components.InboxFilterSheet
import com.otus.mail.feature.inbox.presentation.components.InboxPanel
import com.otus.mail.feature.inbox.presentation.components.InboxPanelActions
import com.otus.mail.feature.inbox.presentation.components.MessagePreview

@Composable
fun InboxScreen(
    messages: List<MailMessage>,
    dateFormatter: DateFormatter = remember { DateFormatter() },
    onOpenNavigation: () -> Unit = {},
    status: InboxStatus = InboxStatus(),
    onRefresh: () -> Unit = {},
    context: InboxContext = InboxContext(),
    messageActions: MessageActions? = null,
) {
    val state =
        rememberSaveable(saver = InboxState.saver(context.defaultSearch)) {
            InboxState(defaultSearch = context.defaultSearch)
        }
    var filter by rememberSaveable { mutableStateOf<InboxFilter?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val closeSearch = {
        state.closeSearch()
        focus.clearFocus()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.searching,
        onBackCompleted = closeSearch,
    )
    ProvideTextStyle(panelTextStyle()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(InboxTokens.pageInset),
        ) {
            InboxPanel(
                state.searching,
                state.search,
                dateFormatter,
                context,
                InboxPanelActions(
                    onChange = state::update,
                    onSearchingChange = { searching -> if (searching) state.startSearch() else closeSearch() },
                    onFilter = {
                        focus.clearFocus()
                        filter = it
                    },
                    onSubmit = { focus.clearFocus() },
                    onOpenNavigation = onOpenNavigation,
                ),
                remember(messages, state.search.senders) { selectedSenders(messages, state.search.senders) },
            )
            PullToRefresh(status.loading, onRefresh, Modifier.weight(1f)) {
                InboxResults(messages, status, state, dateFormatter, context, onOpen = { openId = it.id })
            }
        }
        filter?.let { selected ->
            InboxFilterSheet(selected, messages, state.search, dateFormatter, state::update) { filter = null }
        }
        messages.firstOrNull { it.id == openId }?.let { MessagePreview(it, messageActions) { openId = null } }
    }
}

@Composable
private fun InboxResults(
    messages: List<MailMessage>,
    status: InboxStatus,
    state: InboxState,
    dateFormatter: DateFormatter,
    context: InboxContext,
    onOpen: (MailMessage) -> Unit,
) {
    val results = remember(messages, state.search) { searchMessages(messages, state.search) }
    val inboxScroll = rememberLazyListState()
    val searchScroll = rememberLazyListState()
    val listState = if (state.searching) searchScroll else inboxScroll
    LaunchedEffect(state.search) { listState.scrollToItem(0) }
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
            Modifier.padding(horizontal = InboxTokens.rowPadding, vertical = InboxTokens.rowPadding),
            color = OtusColors.text2,
            fontSize = InboxTokens.metadataSize,
        )
        status.notice?.let {
            Text(
                it,
                Modifier.padding(horizontal = InboxTokens.rowPadding),
                color = OtusColors.text2,
                fontSize = InboxTokens.metadataSize,
            )
        }
        Box(Modifier.weight(1f)) {
            InboxBody(
                results,
                listState,
                dateFormatter,
                onOpen,
                emptyContent = {
                    InboxEmptyState(
                        context.emptyTitle,
                        state.searching,
                        messages.isNotEmpty(),
                        state.filtered,
                        status.connected,
                        onReset = state::reset,
                    )
                },
            )
        }
    }
}
