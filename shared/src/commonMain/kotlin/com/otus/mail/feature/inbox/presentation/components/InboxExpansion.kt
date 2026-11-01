package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.mail.quickview.components.QuickJump
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.filters.components.FilterResetAction
import com.otus.designsystem.shared.filters.components.FloatingFooter
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.rememberPanelExpansion
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.designsystem.theme.tokens.layout.QuickViewTokens
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.QuickViewModule
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.QuickViewContext
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How far the inbox's control panel is dragged open: its usual height, the quick view, and every
 * filter. Past halfway to the filters the panel opens the [filters] draft, and back below it drops
 * it. Back steps down to the usual height. Null for a page without [quick].
 */
@Composable
internal fun rememberInboxExpansion(
    quick: QuickViewContext?,
    filters: InboxFilterState,
): PanelExpansion? {
    val density = LocalDensity.current
    val window =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val modules = quick?.modules?.size ?: 0
    val expansion = rememberPanelExpansion(listOf(quickHeight(modules), window - FILTERS_CLEARANCE))
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

/** What the panel shows in the room it grows: the quick view first, then every filter. */
internal class ExpandedInbox(
    val expansion: PanelExpansion,
    val sources: QuickSources,
    val dateFormatter: DateFormatter,
)

/**
 * The inbox panel's grown room: the quick view's modules and Jump to around the first stop, and
 * the filter sections with their Show button toward the last, each fading with how far the panel
 * is open.
 */
@Composable
internal fun ExpandedInboxContent(inbox: ExpandedInbox) {
    val position = { inbox.expansion.position }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.graphicsLayer { alpha = quickAlpha(position()) }) { QuickViewContent(inbox) }
        val filters = inbox.sources.filters
        if (filters.isOpen) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = filtersAlpha(position()) }) {
                FiltersContent(filters, inbox) { inbox.expansion.collapse() }
            }
        }
    }
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
    onDone: () -> Unit,
) {
    // The last draft stays on screen while the panel closes.
    var shown by remember { mutableStateOf(InboxFilters()) }
    state.draft?.let { shown = it }
    val options = inbox.sources.page.filterOptions
    val people = rememberFilterPeople(state, options, inbox.sources.messages)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = PanelTokens.panelInset), horizontalArrangement = Arrangement.End) {
            FilterResetAction(state::reset, enabled = !shown.isDefault)
        }
        FloatingFooter(
            footer = {
                InboxFilterFooter(state) {
                    onDone()
                    state.show()
                }
            },
            Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = SheetTokens.inset)) {
                FilterSections(
                    shown,
                    options,
                    inbox.dateFormatter,
                    state::edit,
                    PeopleSearches(state.senders, state.recipients, people),
                )
            }
        }
    }
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

/** Room the full panel leaves below it, for the scrim and the screen's bottom. */
private val FILTERS_CLEARANCE = 200.dp
