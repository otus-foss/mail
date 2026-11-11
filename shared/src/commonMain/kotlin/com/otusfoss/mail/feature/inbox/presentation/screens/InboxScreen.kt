package com.otusfoss.mail.feature.inbox.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.shared.actionbar.components.ActionBarHost
import com.otusfoss.core.designsystem.shared.pages.components.SlidingOverlay
import com.otusfoss.core.designsystem.shared.pages.components.belowControlPanel
import com.otusfoss.core.designsystem.shared.pages.elements.hiddenWhen
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.InboxSearch
import com.otusfoss.mail.feature.inbox.domain.scopeToLoad
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.InboxState
import com.otusfoss.mail.feature.inbox.presentation.InboxStatus
import com.otusfoss.mail.feature.inbox.presentation.components.CategoryView
import com.otusfoss.mail.feature.inbox.presentation.components.InboxEmptyState
import com.otusfoss.mail.feature.inbox.presentation.components.InboxHeaderActions
import com.otusfoss.mail.feature.inbox.presentation.components.InboxLayers
import com.otusfoss.mail.feature.inbox.presentation.components.InboxPanel
import com.otusfoss.mail.feature.inbox.presentation.components.InboxPanelActions
import com.otusfoss.mail.feature.inbox.presentation.components.InboxResults
import com.otusfoss.mail.feature.inbox.presentation.components.OpenedMessage
import com.otusfoss.mail.feature.inbox.presentation.components.PanelLook
import com.otusfoss.mail.feature.inbox.presentation.components.PanelOpens
import com.otusfoss.mail.feature.inbox.presentation.components.PanelOverList
import com.otusfoss.mail.feature.inbox.presentation.components.SearchFieldActions
import com.otusfoss.mail.feature.inbox.presentation.components.grownWith
import com.otusfoss.mail.feature.inbox.presentation.components.inboxBar
import com.otusfoss.mail.feature.inbox.presentation.components.panelSelection
import com.otusfoss.mail.feature.inbox.presentation.components.rememberActiveFilters
import com.otusfoss.mail.feature.inbox.presentation.components.rememberCategoryView
import com.otusfoss.mail.feature.inbox.presentation.components.rememberFilterSelection
import com.otusfoss.mail.feature.inbox.presentation.components.rememberHeaderActions
import com.otusfoss.mail.feature.inbox.presentation.components.rememberInboxRows
import com.otusfoss.mail.feature.inbox.presentation.components.rememberUnreadCount
import com.otusfoss.mail.feature.inbox.presentation.listFrame

@Composable
fun InboxScreen(
    messages: List<MailMessage>,
    dateFormatter: DateFormatter = remember { DateFormatter() },
    onOpenNavigation: () -> Unit = {},
    status: InboxStatus = InboxStatus(),
    onRefresh: () -> Unit = {},
    context: InboxContext,
    messageActions: MessageActions? = null,
) {
    val state = rememberInboxState(context.defaultSearch)
    val rows = rememberInboxRows(messages, messageActions, context.onOpenDraft, context.opening)
    val filters = context.filters ?: remember { InboxFilterState() }
    val sorted = rememberCategoryView(context, state, messages, status, filters)
    val unreadCount = rememberUnreadCount(sorted.listed, status.loading)
    val activeFilters = rememberActiveFilters(filters, context.filterOptions, messages, dateFormatter)
    var panelHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val page = shownPage(state, context).sortedInto(sorted)
    val headerActions = rememberHeaderActions(state, page, filters, messages, rows, onOpenNavigation, dateFormatter)
    val selecting = rows.panelSelection(sorted.listed.messages)
    ProvideTextStyle(panelTextStyle()) {
        SlidingOverlay(
            rows.opened,
            Modifier.fillMaxSize(),
            base = {
                ActionBarHost(
                    bar = inboxBar(sorted.listed, state, rows, messageActions, context.onCompose, headerActions),
                    Modifier
                        .fillMaxSize()
                        // The control panel draws behind the status bar and pads itself.
                        .belowControlPanel()
                        // What an open message or an overlay covers, accessibility services skip too.
                        .hiddenWhen(rows.opened != null || headerActions.overlays.coverPage()),
                ) { barPadding ->
                    // Message rows reach the screen edges so a swipe's color does; everything else keeps the inset.
                    PanelOverList(
                        status.loading,
                        rowsShown = sorted.messages.isNotEmpty(),
                        onRefresh,
                        holdInset = selecting != null,
                        expansion = headerActions.expansion,
                        panel = { collapse, modifier ->
                            InboxHeader(
                                messages,
                                state,
                                page,
                                dateFormatter,
                                headerActions,
                                PanelLook(collapse, unreadCount, activeFilters, sorted.tabs, selecting),
                                Modifier
                                    .onSizeChanged { panelHeight = with(density) { it.height.toDp() } }
                                    .then(modifier),
                            )
                        },
                    ) { topInset, collapse ->
                        val frame = page.listFrame(state.searching, topInset, barPadding, collapse)
                        Box(Modifier.hiddenWhen(headerActions.covers(state))) {
                            InboxResults(sorted.listed, status, state, dateFormatter, rows.gestures, frame) {
                                InboxEmptyContent(sorted.messages, status, state, page, filters, onRefresh)
                            }
                        }
                    }
                }
            },
        ) {
            page.readView.Show(OpenedMessage(it, status.notice, state.mixesFolders(filters, page), rows.close))
        }
        InboxLayers(page, panelHeight, headerActions.overlays)
    }
}

/** The page's search state; a new default, such as folders added to the Inbox, starts a fresh search from it. */
@Composable
private fun rememberInboxState(defaultSearch: InboxSearch): InboxState =
    rememberSaveable(
        defaultSearch,
        saver = InboxState.saver(defaultSearch),
    ) { InboxState(defaultSearch = defaultSearch) }

@Composable
private fun inboxPanelActions(
    state: InboxState,
    actions: InboxHeaderActions,
): InboxPanelActions {
    val focus = LocalFocusManager.current
    return InboxPanelActions(
        onChange = state::update,
        onSearchingChange = { searching -> if (searching) state.startSearch() else actions.closeSearch() },
        onEditSearch = {
            focus.clearFocus()
            state.editSearch()
        },
        onOpenFilters = {
            focus.clearFocus()
            actions.openFilters()
        },
        field =
            SearchFieldActions(
                onSubmit = {
                    focus.clearFocus()
                    if (state.searchSurfaceOpen) state.showResults()
                },
                // Over the results, focusing the field opens the search room again.
                onFocus = state::editSearch,
            ),
        onFolder = state::showFolder,
        opens =
            PanelOpens(actions.onOpenNavigation) {
                focus.clearFocus()
                actions.overlays.accountsOpen = true
            },
    )
}

@Composable
private fun InboxHeader(
    messages: List<MailMessage>,
    state: InboxState,
    context: InboxContext,
    dateFormatter: DateFormatter,
    actions: InboxHeaderActions,
    look: PanelLook,
    modifier: Modifier,
) {
    Box(modifier) {
        InboxPanel(
            state.searching,
            state.search,
            dateFormatter,
            context,
            inboxPanelActions(state, actions),
            rememberFilterSelection(messages, state.search, context.mailboxSearch.accounts),
            look.grownWith(
                // Over a search's results the panel does not drag open, but the filter button still grows it.
                actions.expansion.takeIf { !state.resultsShown || it.expanded },
                actions.grown,
                state.searchSurfaceOpen,
            ),
        )
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
        context.copy(title = it.label, emptyTitle = "${it.label} is empty", folderPill = it)
    } ?: context
}

/** This page naming an empty category pill, and paging for the pill's share of the mail. */
private fun InboxContext.sortedInto(view: CategoryView): InboxContext =
    copy(emptyTitle = view.tabs?.emptyTitle ?: emptyTitle, onLoadMore = view.onLoadMore)

/** Whether search, folder filters, or a folder pill mix folders, so an opened row names none it came from. */
private fun InboxState.mixesFolders(
    filters: InboxFilterState,
    page: InboxContext,
): Boolean = searching || filters.applied.folders.isNotEmpty() || page.folderPill != null
