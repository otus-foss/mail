package com.otus.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.PullToRefresh
import com.otus.designsystem.components.SlidingOverlay
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.scopeToLoad
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.InboxBar
import com.otus.mail.feature.inbox.presentation.components.InboxBody
import com.otus.mail.feature.inbox.presentation.components.InboxEmptyState
import com.otus.mail.feature.inbox.presentation.components.InboxFilter
import com.otus.mail.feature.inbox.presentation.components.InboxFilterSheet
import com.otus.mail.feature.inbox.presentation.components.InboxPanel
import com.otus.mail.feature.inbox.presentation.components.InboxPanelActions
import com.otus.mail.feature.inbox.presentation.components.InboxResults
import com.otus.mail.feature.inbox.presentation.components.RowGestures
import com.otus.mail.feature.inbox.presentation.components.SelectionBar
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
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val opened = messages.firstOrNull { it.id == openId }
    val selected = remember(messages, selectedIds) { messages.filter { it.id in selectedIds } }
    val clearSelection = { selectedIds = emptyList() }
    val gestures =
        rowGestures(
            messageActions,
            selected,
            onToggle = { selectedIds = selectedIds.toggled(it.id) },
            onSelect = { selectedIds = it.toList() },
        ) { openId = it.id }
    val page = shownPage(state, context)
    val closeSearch = searchCloser(state)
    BackHandler(selected.isNotEmpty(), clearSelection)
    ProvideTextStyle(panelTextStyle()) {
        SlidingOverlay(
            opened,
            Modifier.fillMaxSize(),
            base = {
                ActionBarHost(
                    bar =
                        inboxBar(messages, state, selected, messageActions, clearSelection, context.onCompose) {
                            filter = InboxFilter.More
                        },
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        // An open message covers the list, so accessibility services skip it too.
                        .then(if (opened == null) Modifier else Modifier.clearAndSetSemantics {}),
                ) { barPadding ->
                    Column(Modifier.fillMaxSize().padding(vertical = InboxTokens.pageInset)) {
                        // Message rows reach the screen edges so a swipe's color does; everything else keeps the inset.
                        InboxHeader(messages, state, page, dateFormatter, closeSearch, onOpenNavigation) { filter = it }
                        PullToRefresh(status.loading, onRefresh, Modifier.weight(1f)) {
                            InboxResults(
                                messages,
                                status,
                                state,
                                dateFormatter,
                                gestures,
                                barPadding,
                                emptyContent = { InboxEmptyContent(messages, status, state, page, onRefresh) },
                            )
                        }
                    }
                }
            },
        ) { MessageScreen(it, messageActions, status.notice, context.onCompose) { openId = null } }
        filter?.let { selected ->
            InboxFilterSheet(selected, messages, state.search, page, dateFormatter, state::update) { filter = null }
        }
    }
}

/** Closes [state]'s search and the keyboard, also on system back while searching. */
@Composable
private fun searchCloser(state: InboxState): () -> Unit {
    val focus = LocalFocusManager.current
    val closeSearch = {
        state.closeSearch()
        focus.clearFocus()
    }
    BackHandler(state.searching, closeSearch)
    return closeSearch
}

/** Runs [onBack] on system back while [enabled]. */
@Composable
private fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
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
    closeSearch: () -> Unit,
    onOpenNavigation: () -> Unit,
    onFilter: (InboxFilter) -> Unit,
) {
    val actions = inboxPanelActions(state, closeSearch, onOpenNavigation, onFilter)
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

/**
 * The bar under the list: the selection's while rows are selected, none while searching, and the
 * list's otherwise. A minimized bar stays while searching, so it can sink under the keyboard
 * instead of vanishing. Without [actions] there is no bar.
 */
private fun inboxBar(
    messages: List<MailMessage>,
    state: InboxState,
    selected: List<MailMessage>,
    actions: MessageActions?,
    clearSelection: () -> Unit,
    onCompose: (ComposeRequest) -> Unit,
    onFilters: () -> Unit,
): (@Composable () -> Unit)? =
    when {
        actions == null || (state.searching && !actions.behavior().minimizedBar) -> {
            null
        }

        selected.isNotEmpty() -> {
            { SelectionBar(selected, actions, clearSelection) }
        }

        else -> {
            { InboxBar(messages, state, actions, onFilters, onCompose) }
        }
    }

/** Row taps, long presses, and drags: opening marks a message read as the saved behavior asks. */
private fun rowGestures(
    actions: MessageActions?,
    selected: List<MailMessage>,
    onToggle: (MailMessage) -> Unit,
    onSelect: (Set<String>) -> Unit,
    onOpen: (MailMessage) -> Unit,
): RowGestures =
    RowGestures(actions, selected.map { it.id }.toSet(), onToggle, onSelect) { message ->
        actions?.opened(message)
        onOpen(message)
    }

private fun List<String>.toggled(id: String): List<String> = if (id in this) this - id else this + id
