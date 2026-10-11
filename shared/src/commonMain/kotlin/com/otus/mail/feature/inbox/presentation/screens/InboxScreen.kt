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
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.PullToRefresh
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.scopeToLoad
import com.otus.mail.feature.inbox.domain.searchMessages
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
import com.otus.mail.feature.inbox.presentation.components.rememberFilterSelection

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
    val page = shownPage(state, context)
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
                .padding(vertical = InboxTokens.pageInset),
        ) {
            // Message rows reach the screen edges so a swipe's color does; everything else keeps the inset.
            InboxHeader(
                messages,
                state,
                page,
                dateFormatter,
                inboxPanelActions(state, closeSearch, onOpenNavigation) { filter = it },
            )
            PullToRefresh(status.loading, onRefresh, Modifier.weight(1f)) {
                InboxResults(
                    messages,
                    status,
                    state,
                    dateFormatter,
                    messageActions,
                    onOpen = {
                        messageActions?.opened(it)
                        openId = it.id
                    },
                    emptyContent = { InboxEmptyContent(messages, status, state, page, onRefresh) },
                )
            }
        }
        filter?.let { selected ->
            InboxFilterSheet(selected, messages, state.search, page, dateFormatter, state::update) { filter = null }
        }
        messages.firstOrNull { it.id == openId }?.let { MessagePreview(it, messageActions) { openId = null } }
    }
}

@Composable
private fun inboxPanelActions(
    state: InboxState,
    closeSearch: () -> Unit,
    onOpenNavigation: () -> Unit,
    onFilter: (InboxFilter) -> Unit,
): InboxPanelActions {
    val focus = LocalFocusManager.current
    return InboxPanelActions(
        onChange = state::update,
        onSearchingChange = { searching -> if (searching) state.startSearch() else closeSearch() },
        onFilter = {
            focus.clearFocus()
            onFilter(it)
        },
        onSubmit = { focus.clearFocus() },
        onOpenNavigation = onOpenNavigation,
        onFolder = state::showFolder,
    )
}

@Composable
private fun InboxHeader(
    messages: List<MailMessage>,
    state: InboxState,
    context: InboxContext,
    dateFormatter: DateFormatter,
    actions: InboxPanelActions,
) {
    Box(Modifier.padding(horizontal = InboxTokens.pageInset)) {
        InboxPanel(
            state.searching,
            state.search,
            dateFormatter,
            context,
            actions,
            rememberFilterSelection(messages, state.search, context.mailboxSearch.accounts),
        )
    }
}

@Composable
private fun InboxEmptyContent(
    messages: List<MailMessage>,
    status: InboxStatus,
    state: InboxState,
    context: InboxContext,
    onRefresh: () -> Unit,
) {
    InboxEmptyState(
        context.emptyTitle,
        state.searching,
        messages.isNotEmpty(),
        state.filtered,
        status,
        onReset = state::reset,
        onRefresh = onRefresh,
    )
}

/**
 * [context] showing the selected folder pill and named after it. This loads the pill's folders, or
 * the mailboxes a search picks beyond the page's own, and the page's own again once neither applies.
 * The pill is dropped once the user takes it off the row.
 */
@Composable
private fun shownPage(
    state: InboxState,
    context: InboxContext,
): InboxContext {
    val pills = context.pills
    val shown = pills.option(state.folderPill)
    val scope = shown?.scopeFor(state.search.accounts) ?: state.search.scopeToLoad(context.defaultSearch)
    LaunchedEffect(scope) { context.mailboxSearch.load(scope) }
    LaunchedEffect(pills.pills, pills.folders, pills.foldersLoaded) {
        val unavailable = pills.foldersLoaded && shown == null
        val selected = state.folderPill
        if (selected != null && (selected !in pills.pills || unavailable)) {
            state.showFolder(null)
        }
    }
    return shown?.let {
        context.copy(searchPrompt = "Search ${it.label}", emptyTitle = "${it.label} is empty", folderPill = it)
    } ?: context
}

@Composable
private fun InboxResults(
    messages: List<MailMessage>,
    status: InboxStatus,
    state: InboxState,
    dateFormatter: DateFormatter,
    actions: MessageActions?,
    onOpen: (MailMessage) -> Unit,
    emptyContent: @Composable () -> Unit,
) {
    val matching = state.matching
    val results = remember(messages, matching) { searchMessages(messages, matching) }
    val inboxScroll = rememberLazyListState()
    val searchScroll = rememberLazyListState()
    val listState = if (state.searching) searchScroll else inboxScroll
    LaunchedEffect(state.search, state.folderPill) { listState.scrollToItem(0) }
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
                onOpen,
                actions,
                emptyContent = emptyContent,
            )
        }
    }
}
