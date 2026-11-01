package com.otus.mail.feature.inbox.presentation.screens

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.actionbar.components.ActionBarHost
import com.otus.designsystem.shared.pages.components.SlidingOverlay
import com.otus.designsystem.shared.pages.components.belowControlPanel
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.scopeToLoad
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.MessageActions
import com.otus.mail.feature.inbox.presentation.components.BackHandler
import com.otus.mail.feature.inbox.presentation.components.ExpandedInbox
import com.otus.mail.feature.inbox.presentation.components.ExpandedInboxContent
import com.otus.mail.feature.inbox.presentation.components.HitDates
import com.otus.mail.feature.inbox.presentation.components.InboxEmptyState
import com.otus.mail.feature.inbox.presentation.components.InboxHeaderActions
import com.otus.mail.feature.inbox.presentation.components.InboxLayers
import com.otus.mail.feature.inbox.presentation.components.InboxOverlays
import com.otus.mail.feature.inbox.presentation.components.InboxPanel
import com.otus.mail.feature.inbox.presentation.components.InboxPanelActions
import com.otus.mail.feature.inbox.presentation.components.InboxResults
import com.otus.mail.feature.inbox.presentation.components.InboxRows
import com.otus.mail.feature.inbox.presentation.components.ListedMail
import com.otus.mail.feature.inbox.presentation.components.PanelLook
import com.otus.mail.feature.inbox.presentation.components.PanelOpens
import com.otus.mail.feature.inbox.presentation.components.PanelOverList
import com.otus.mail.feature.inbox.presentation.components.QuickSources
import com.otus.mail.feature.inbox.presentation.components.grownWith
import com.otus.mail.feature.inbox.presentation.components.inboxBar
import com.otus.mail.feature.inbox.presentation.components.panelSelection
import com.otus.mail.feature.inbox.presentation.components.rememberFilterAnchor
import com.otus.mail.feature.inbox.presentation.components.rememberFilterSelection
import com.otus.mail.feature.inbox.presentation.components.rememberHeaderActions
import com.otus.mail.feature.inbox.presentation.components.rememberInboxExpansion
import com.otus.mail.feature.inbox.presentation.components.rememberInboxOverlays
import com.otus.mail.feature.inbox.presentation.components.rememberInboxRows
import com.otus.mail.feature.inbox.presentation.components.rememberUnreadCount
import com.otus.mail.feature.inbox.presentation.listFrame

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
    // A new default, such as folders added to what the Inbox shows, starts a fresh search from it.
    val state =
        rememberSaveable(context.defaultSearch, saver = InboxState.saver(context.defaultSearch)) {
            InboxState(defaultSearch = context.defaultSearch)
        }
    val rows = rememberInboxRows(messages, messageActions)
    val filters = context.filters ?: remember { InboxFilterState() }
    val listed = ListedMail(messages, filters.applied)
    val unreadCount = rememberUnreadCount(listed, status.loading)
    val anchor = rememberFilterAnchor(filters, context.filterOptions, messages, dateFormatter)
    val page = shownPage(state, context)
    val headerActions = rememberHeaderActions(state, page, filters, messages, rows, onOpenNavigation, dateFormatter)
    val overlays = headerActions.overlays
    val selecting = rows.panelSelection(listed.messages)
    ProvideTextStyle(panelTextStyle()) {
        SlidingOverlay(
            rows.opened,
            Modifier.fillMaxSize(),
            base = {
                ActionBarHost(
                    bar = inboxBar(listed, state, rows, messageActions, context.onCompose, headerActions::openFilters),
                    Modifier
                        .fillMaxSize()
                        // The control panel draws behind the status bar and pads itself.
                        .belowControlPanel()
                        .hiddenWhenCovered(rows.opened != null || headerActions.covers(state)),
                ) { barPadding ->
                    // Message rows reach the screen edges so a swipe's color does; everything else keeps the inset.
                    PanelOverList(
                        status.loading,
                        rowsShown = messages.isNotEmpty(),
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
                                PanelLook(collapse, unreadCount, anchor.filters, selecting, state.resultsShown),
                                anchor.measure.then(modifier),
                            )
                        },
                    ) { topInset, collapse ->
                        val frame = context.listFrame(state.searching, topInset, barPadding, collapse)
                        InboxResults(listed, status, state, dateFormatter, rows.gestures, frame) {
                            InboxEmptyContent(messages, status, state, page, filters, onRefresh)
                        }
                    }
                }
            },
        ) {
            val cameFrom = page.cameFrom(it, state.searching || filters.applied.folders.isNotEmpty())
            MessageScreen(it, messageActions, status.notice, page, cameFrom, rows.close)
        }
        InboxLayers(state, listed, anchor, page, HitDates(dateFormatter, context.rowLook.layout), overlays, rows)
    }
}

/** An open message or the filter panel covers the list, so accessibility services skip it too. */
private fun Modifier.hiddenWhenCovered(covered: Boolean): Modifier = if (covered) clearAndSetSemantics {} else this

/**
 * The folders this page shows [message] from. Search, folder pills, and the filter panel's folder
 * choices, which [filtered] says are on, mix folders, so they give none.
 */
private fun InboxContext.cameFrom(
    message: MailMessage,
    filtered: Boolean,
): Set<String> =
    read
        ?.folders
        ?.cameFrom
        ?.invoke(message)
        ?.takeIf { !filtered && folderPill == null }
        .orEmpty()

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
            actions.overlays.filter = it
        },
        onOpenFilters = {
            focus.clearFocus()
            actions.openFilters()
        },
        onSubmit = { focus.clearFocus() },
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
            // Under the search surface the panel stays as it was, and takes the search once results show.
            state.resultsShown,
            state.search,
            dateFormatter,
            context,
            inboxPanelActions(state, actions),
            rememberFilterSelection(messages, state.search, context.mailboxSearch.accounts),
            look.grownWith(actions.expansion, actions.grown),
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
