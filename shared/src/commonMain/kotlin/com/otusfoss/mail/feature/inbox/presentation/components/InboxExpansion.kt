package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.mail.quickview.components.QuickJump
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_filter_list
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.IconButton
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.panels.components.GROWN_LAST_STOP
import com.otusfoss.core.designsystem.shared.panels.components.GROWN_LAST_STOP_FROM
import com.otusfoss.core.designsystem.shared.panels.components.GrownPanelLayers
import com.otusfoss.core.designsystem.shared.panels.components.PanelExpansion
import com.otusfoss.core.designsystem.shared.panels.components.grownPanelHeight
import com.otusfoss.core.designsystem.shared.panels.components.quickViewHeight
import com.otusfoss.core.designsystem.shared.panels.components.rememberPanelExpansion
import com.otusfoss.core.designsystem.shared.panels.components.rememberShownUntilClosed
import com.otusfoss.core.designsystem.theme.tokens.layout.PanelTokens
import com.otusfoss.core.designsystem.theme.tokens.layout.QuickViewTokens
import com.otusfoss.core.designsystem.theme.tokens.overlays.ActionBarTokens
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.QuickViewContext
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How far the inbox's control panel is dragged open: its usual height, the quick view, or the
 * search room [searchRoom] tall while a search is in it, [typed] or not, and every filter. Past halfway to the
 * filters the panel opens the [filters] draft, and back below it drops it. Back steps down to the
 * usual height.
 */
@Composable
internal fun rememberInboxExpansion(
    quick: QuickViewContext,
    filters: InboxFilterState,
    searchRoom: Dp?,
    typed: Boolean,
    onLeaveSearch: () -> Unit,
): PanelExpansion {
    val modules = quick.modules.size
    val full = filtersHeight()
    val first = searchRoom?.coerceAtMost(full) ?: quickViewHeight(modules)
    // Typing changes what the search room shows; the keyboard only moves its edge.
    val expansion = rememberPanelExpansion(listOf(first, full), content = (searchRoom != null) to typed)
    FollowSearch(expansion, searchRoom != null, onLeaveSearch)
    val focus = LocalFocusManager.current
    LaunchedEffect(expansion) {
        // Dragged past the first stop, or sent to every filter, the panel heads for the filters: the
        // keyboard goes first, or the page scrolls to keep the focused field in view. A stop the
        // keyboard moves, or a search room shrinking as its text is cleared, is neither.
        snapshotFlow { expansion.position > 1f && (expansion.dragging || expansion.stop == GROWN_LAST_STOP) }
            .distinctUntilChanged()
            .collect { past -> if (past) focus.clearFocus() }
    }
    LaunchedEffect(expansion, filters) {
        snapshotFlow { expansion.position >= GROWN_LAST_STOP_FROM }.distinctUntilChanged().collect { full ->
            when {
                full && !filters.isOpen && (expansion.dragging || expansion.stop == GROWN_LAST_STOP) -> filters.open()
                !full && filters.isOpen -> filters.close()
            }
        }
    }
    // While a search is in the panel, back ends the search, which closes the panel too.
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = expansion.expanded && searchRoom == null,
        onBackCompleted = expansion::collapse,
    )
    return expansion
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
    val filters = inbox.sources.filters
    GrownPanelLayers(
        inbox.expansion,
        lastStopOpen = filters.isOpen,
        quick = {
            val searching = rememberShownUntilClosed(inbox.expansion, inbox.room.state.searchSurfaceOpen)
            if (searching) SearchRoomContent(inbox.room) else QuickViewContent(inbox)
        },
        lastStop = { FiltersContent(filters, inbox) },
    )
}

@Composable
private fun QuickViewContent(inbox: ExpandedInbox) {
    val sources = inbox.sources
    val quick = sources.page.quick
    Column(
        Modifier.fillMaxWidth().sheetInset().padding(top = QuickViewTokens.moduleGap),
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
        val allFilters = @Composable {
            IconButton(
                "All filters",
                { inbox.expansion.expandTo(GROWN_LAST_STOP) },
            ) { Symbol(Res.drawable.symbol_filter_list) }
        }
        QuickJump(allFilters) {
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
            .sheetInset()
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
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bar = ActionBarTokens.height + ActionBarTokens.barInset * 2
    return grownPanelHeight(above = top + PanelTokens.headerRowHeight, below = bottom + bar)
}
