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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ActionBarHost
import com.otus.designsystem.components.PullToRefresh
import com.otus.designsystem.components.SlidingOverlay
import com.otus.designsystem.theme.FilterTokens
import com.otus.designsystem.theme.InboxTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.scopeToLoad
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.ActiveFilters
import com.otus.mail.feature.inbox.presentation.components.BackHandler
import com.otus.mail.feature.inbox.presentation.components.InboxBody
import com.otus.mail.feature.inbox.presentation.components.InboxEmptyState
import com.otus.mail.feature.inbox.presentation.components.InboxFilter
import com.otus.mail.feature.inbox.presentation.components.InboxFilterPanel
import com.otus.mail.feature.inbox.presentation.components.InboxFilterSheet
import com.otus.mail.feature.inbox.presentation.components.InboxPanel
import com.otus.mail.feature.inbox.presentation.components.InboxPanelActions
import com.otus.mail.feature.inbox.presentation.components.InboxResults
import com.otus.mail.feature.inbox.presentation.components.inboxBar
import com.otus.mail.feature.inbox.presentation.components.rememberFilterSelection
import com.otus.mail.feature.inbox.presentation.components.rowGestures
import com.otus.mail.feature.inbox.presentation.components.toggled

@Composable
fun InboxScreen(
    messages: List<MailMessage>,
    dateFormatter: DateFormatter = remember { DateFormatter() },
    onOpenNavigation: () -> Unit = {},
    status: InboxStatus = InboxStatus(),
    onRefresh: () -> Unit = {},
    context: InboxContext = InboxContext(),
    messageActions: MessageActions? = null,
    filters: InboxFilterState = remember { InboxFilterState() },
    filterOptions: FilterOptions = FilterOptions(),
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
    val filterButton = remember { FocusRequester() }
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    ReturnFocus(filters.isOpen, filterButton)
    BackHandler(selected.isNotEmpty(), clearSelection)
    ProvideTextStyle(panelTextStyle()) {
        SlidingOverlay(
            opened,
            Modifier.fillMaxSize(),
            base = {
                ActionBarHost(
                    bar =
                        inboxBar(
                            messages,
                            state,
                            selected,
                            messageActions,
                            clearSelection,
                            context.onCompose,
                            filters.applied,
                            filters::open,
                        ),
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .imePadding()
                        // An open message covers the list, so accessibility services skip it too.
                        .then(if (opened == null && !filters.isOpen) Modifier else Modifier.clearAndSetSemantics {}),
                ) { barPadding ->
                    Column(Modifier.fillMaxSize().padding(vertical = InboxTokens.pageInset)) {
                        // Message rows reach the screen edges so a swipe's color does; everything else keeps the inset.
                        InboxHeader(
                            messages,
                            state,
                            page,
                            dateFormatter,
                            InboxHeaderActions(closeSearch, onOpenNavigation, { filter = it }, filters::open),
                            ActiveFilters(filters.applied, filterOptions, dateFormatter, filters::update),
                            filterButton,
                            Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                        )
                        PullToRefresh(status.loading, onRefresh, Modifier.weight(1f)) {
                            InboxResults(
                                messages,
                                status,
                                state,
                                filters.applied,
                                dateFormatter,
                                gestures,
                                barPadding,
                                emptyContent = { InboxEmptyContent(messages, status, state, page, filters, onRefresh) },
                            )
                        }
                    }
                }
            },
        ) { MessageScreen(it, messageActions, status.notice, context.loadHtml, context.onCompose) { openId = null } }
        InboxFilterPanel(
            filters,
            filterOptions,
            dateFormatter,
            headerHeight,
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .padding(start = InboxTokens.pageInset, end = InboxTokens.pageInset, top = InboxTokens.pageInset)
                .padding(bottom = FilterTokens.screenInset),
        )
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

/** What the control panel's buttons do besides editing the search. */
private class InboxHeaderActions(
    val closeSearch: () -> Unit,
    val onOpenNavigation: () -> Unit,
    val onFilter: (InboxFilter) -> Unit,
    val onOpenFilters: () -> Unit,
)

@Composable
private fun inboxPanelActions(
    state: InboxState,
    actions: InboxHeaderActions,
): InboxPanelActions {
    val focus = LocalFocusManager.current
    return InboxPanelActions(
        onChange = state::update,
        onSearchingChange = { searching -> if (searching) state.startSearch() else actions.closeSearch() },
        onFilter = {
            focus.clearFocus()
            actions.onFilter(it)
        },
        onOpenFilters = {
            focus.clearFocus()
            actions.onOpenFilters()
        },
        onSubmit = { focus.clearFocus() },
        onOpenNavigation = actions.onOpenNavigation,
        onFolder = state::showFolder,
    )
}

@Composable
private fun InboxHeader(
    messages: List<MailMessage>,
    state: InboxState,
    context: InboxContext,
    dateFormatter: DateFormatter,
    actions: InboxHeaderActions,
    filters: ActiveFilters,
    filterButton: FocusRequester,
    modifier: Modifier,
) {
    Box(modifier.padding(horizontal = InboxTokens.pageInset)) {
        InboxPanel(
            state.searching,
            state.search,
            dateFormatter,
            context,
            inboxPanelActions(state, actions),
            rememberFilterSelection(messages, state.search, context.mailboxSearch.accounts),
            filters,
            filterButton,
        )
    }
}

/** Gives focus back to the filter button once the panel that opened from it closes. */
@Composable
private fun ReturnFocus(
    open: Boolean,
    button: FocusRequester,
) {
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        // The button is missing only while the page is leaving, when focus does not matter.
        if (wasOpen && !open) button.runCatching { requestFocus() }
        wasOpen = open
    }
}

@Composable
private fun InboxEmptyContent(
    messages: List<MailMessage>,
    status: InboxStatus,
    state: InboxState,
    context: InboxContext,
    filters: InboxFilterState,
    onRefresh: () -> Unit,
) {
    // The server leaves out mail the applied filters hide, so an empty list may still be a filtered one.
    val narrowed = !filters.applied.isDefault
    InboxEmptyState(
        context.emptyTitle,
        state.searching,
        messages.isNotEmpty() || narrowed,
        state.filtered || narrowed,
        status,
        onReset = {
            state.reset()
            filters.update(InboxFilters())
        },
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
