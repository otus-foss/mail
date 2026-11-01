package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.rememberPanelExpansion
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.ActionBarTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.QuickViewModule
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.QuickViewContext
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How far the inbox's control panel is dragged open: its usual height, the quick view, or the
 * search room [searchRoom] tall while a search is in it, [typed] or not, and every filter. Past halfway to the
 * filters the panel opens the [filters] draft, and back below it drops it. Back steps down to the
 * usual height. Null for a page without [quick].
 */
@Composable
internal fun rememberInboxExpansion(
    quick: QuickViewContext?,
    filters: InboxFilterState,
    searchRoom: Dp?,
    typed: Boolean,
    onLeaveSearch: () -> Unit,
): PanelExpansion? {
    val modules = quick?.modules?.size ?: 0
    val full = filtersHeight()
    val first = searchRoom?.coerceAtMost(full) ?: quickHeight(modules)
    // Typing changes what the search room shows; the keyboard only moves its edge.
    val expansion = rememberPanelExpansion(listOf(first, full), content = (searchRoom != null) to typed)
    FollowSearch(expansion, searchRoom != null, onLeaveSearch)
    val focus = LocalFocusManager.current
    LaunchedEffect(expansion) {
        // Past the first stop the panel heads for every filter: the keyboard goes first, or the page
        // scrolls to keep the focused field in view as the panel grows under the keyboard.
        snapshotFlow { expansion.position > 1f }.distinctUntilChanged().collect { past -> if (past) focus.clearFocus() }
    }
    LaunchedEffect(expansion, filters) {
        snapshotFlow { expansion.position >= FILTERS_FROM }.distinctUntilChanged().collect { full ->
            when {
                full && !filters.isOpen -> filters.open()
                !full && filters.isOpen -> filters.close()
            }
        }
    }
    BackHandler(expansion.expanded) { expansion.collapse() }
    return expansion.takeIf { quick != null }
}

/** What the panel shows in the room it grows: the quick view or the search [room] first, then every filter. */
internal class ExpandedInbox(
    val expansion: PanelExpansion,
    val sources: QuickSources,
    val room: SearchRoom,
    val dateFormatter: DateFormatter,
)

/**
 * The inbox panel's grown room: the quick view's modules and Jump to around the first stop, and
 * the filter sections toward the last, each fading with how far the panel is open. The action
 * bar's place holds the filters' Reset and Show buttons meanwhile.
 */
@Composable
internal fun ExpandedInboxContent(inbox: ExpandedInbox) {
    val position = { inbox.expansion.position }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.graphicsLayer { alpha = if (inbox.expansion.skipping) 0f else quickAlpha(position()) }) {
            if (rememberSearchShown(inbox)) SearchRoomContent(inbox.room) else QuickViewContent(inbox)
        }
        val filters = inbox.sources.filters
        if (filters.isOpen) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = filtersAlpha(position()) }) {
                FiltersContent(filters, inbox)
            }
        }
    }
}

/** Whether the search room shows: while the search is in it, and until the panel has closed after. */
@Composable
private fun rememberSearchShown(inbox: ExpandedInbox): Boolean {
    val searching = inbox.room.state.searchSurfaceOpen
    val expanded by remember(inbox.expansion) { derivedStateOf { inbox.expansion.expanded } }
    var shown by remember { mutableStateOf(searching) }
    if (searching) {
        shown = true
    } else if (!expanded) {
        shown = false
    }
    return shown
}

@Composable
private fun QuickViewContent(inbox: ExpandedInbox) {
    val sources = inbox.sources
    val quick = sources.page.quick ?: return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SheetTokens.inset).padding(top = QuickViewTokens.moduleGap),
        verticalArrangement = Arrangement.spacedBy(QuickViewTokens.moduleGap),
    ) {
        quick.modules.forEach { module ->
            when (module) {
                QuickViewModule.Counts -> QuickCountsModule(sources)
                QuickViewModule.People -> QuickPeopleModule(sources)
                QuickViewModule.Addresses -> QuickAddressesModule(sources)
                QuickViewModule.RecentFiles -> QuickFilesModule(sources)
            }
        }
        QuickJump({ inbox.expansion.expandTo(FILTERS_STOP) }) {
            quick.targets.forEach { target ->
                Chip(target.title, {
                    sources.close()
                    target.onOpen()
                }) { Symbol(target.icon) }
            }
        }
    }
}

@Composable
private fun FiltersContent(
    state: InboxFilterState,
    inbox: ExpandedInbox,
) {
    // The last draft stays on screen while the panel closes.
    var shown by remember { mutableStateOf(InboxFilters()) }
    state.draft?.let { shown = it }
    val options = inbox.sources.page.filterOptions
    val people = rememberFilterPeople(state, options, inbox.sources.messages)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SheetTokens.inset)
            .padding(top = QuickViewTokens.moduleGap),
    ) {
        FilterSections(
            shown,
            options,
            inbox.dateFormatter,
            state::edit,
            PeopleSearches(state.senders, state.recipients, people),
        )
    }
}

/**
 * The most the panel grows for every filter: the window below the header row, less the Show button
 * in the action bar's place and the gap above it, so the filters scroll instead of running under it.
 */
@Composable
private fun filtersHeight(): Dp {
    val window =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bar = ActionBarTokens.height + ActionBarTokens.barInset * 2
    return window - top - PanelTokens.headerRowHeight - bottom - bar
}

/** The quick view's height with [modules] picked: the modules, Jump to, and the grab handle. */
private fun quickHeight(modules: Int): Dp =
    QuickViewTokens.moduleGap +
        (QuickViewTokens.moduleHeight + QuickViewTokens.moduleGap) * modules +
        QuickViewTokens.jumpHeight +
        PanelTokens.handlePadding * 2 +
        PanelTokens.handleHeight

private fun quickAlpha(position: Float): Float =
    when {
        position <= QUICK_FROM -> 0f
        position <= 1f -> (position - QUICK_FROM) / (1f - QUICK_FROM)
        else -> (1f - (position - 1f) / (FILTERS_FROM - 1f)).coerceAtLeast(0f)
    }

private fun filtersAlpha(position: Float): Float =
    ((position - FILTERS_FROM) / (FILTERS_STOP - FILTERS_FROM)).coerceIn(0f, 1f)

/** The stop that shows every filter. */
private const val FILTERS_STOP = 2

/** Where the quick view starts to show, as the panel leaves its usual height. */
private const val QUICK_FROM = 0.3f

/** Where the filters take over from the quick view, halfway to their stop. */
private const val FILTERS_FROM = 1.5f
