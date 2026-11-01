package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.input.elements.SearchField
import com.otus.designsystem.shared.overlays.components.SheetHeaderAction
import com.otus.designsystem.shared.panels.components.AccountStack
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.shared.panels.components.PanelExpansion
import com.otus.designsystem.shared.panels.components.PanelMotion
import com.otus.designsystem.shared.panels.components.PanelSearchField
import com.otus.designsystem.shared.panels.components.PanelSearchPill
import com.otus.designsystem.shared.panels.elements.PanelNavigation
import com.otus.designsystem.shared.panels.elements.PanelNavigationIcon
import com.otus.designsystem.shared.panels.elements.PanelTitle
import com.otus.designsystem.theme.motion.LocalMotionScale
import com.otus.designsystem.theme.motion.Motion
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext

/**
 * The inbox's control panel: the chevron, the "Search Inbox" pill, and the account stack, with the
 * pills across its edge. It collapses with the list by [PanelLook.collapse]. While rows are
 * selected it shows the selection instead, and while [searching] the search field and its filters.
 */
@Composable
fun InboxPanel(
    searching: Boolean,
    search: InboxSearch,
    dateFormatter: DateFormatter,
    context: InboxContext,
    actions: InboxPanelActions,
    selection: FilterSelection = FilterSelection(),
    look: PanelLook = PanelLook(),
) {
    val selecting = look.selecting
    if (selecting != null) {
        SelectionPanel(selecting)
        return
    }
    // While the panel grows a search room it keeps its pills; the search's own chips come with the results.
    val searchChips = searching && look.expansion == null
    val railShown = searchChips || context.pills.shown.isNotEmpty()
    val scope = context.scope
    ContextPanel(
        // Without pills, the filter button moves into the header and the list gets the row's space.
        placement = if (railShown) ChipPlacement.Overlapping else ChipPlacement.None,
        navigation = {
            InboxNavigation(
                searching,
                actions.opens.onOpenNavigation,
            ) { actions.onSearchingChange(false) }
        },
        trailingAction = {
            FilterButton(
                look.filters,
                actions.onOpenFilters,
                Modifier.focusRequester(look.filters.button),
                IconButtonLook.Outlined,
            )
        },
        chips = { InboxPanelChips(searchChips, search, context, dateFormatter, selection, actions, look) },
        motion = PanelMotion(look.collapse, look.expansion, look.expanded),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InboxHeaderField(searching, search, context, actions, look, filterInPill = railShown, Modifier.weight(1f))
            scope?.let { InboxAccountStack(it, look.collapse, actions.opens.onOpenAccounts) }
        }
    }
}

/**
 * The search field while [searching], else the search pill, which takes the filter button once the
 * pills fade on scroll when [filterInPill].
 */
@Composable
private fun InboxHeaderField(
    searching: Boolean,
    search: InboxSearch,
    context: InboxContext,
    actions: InboxPanelActions,
    look: PanelLook,
    filterInPill: Boolean,
    modifier: Modifier,
) {
    val motionScale = LocalMotionScale.current
    AnimatedContent(
        targetState = searching,
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            (
                fadeIn(Motion.tween(scale = motionScale)) togetherWith
                    fadeOut(Motion.tween(scale = motionScale))
            ).using(SizeTransform(clip = false) { _, _ -> Motion.tween(scale = motionScale) })
        },
        label = "Inbox search mode",
    ) { mode ->
        if (mode) {
            InboxSearchInput(
                search.query,
                { actions.onChange(search.copy(query = it)) },
                actions.field.onSubmit,
                active = look.searchFocused,
                onFocus = actions.field.onFocus,
            )
        } else {
            PanelSearchPill(
                context.searchPrompt,
                onClick = { actions.onSearchingChange(true) },
                collapse = look.collapse,
                filter = if (filterInPill) ({ FilterButton(look.filters, actions.onOpenFilters) }) else null,
            )
        }
    }
}

/**
 * Opens the filter panel; a dot shows while filters narrow the list. It is [IconButtonLook.Bare]
 * by default for the search pill, which has its own fill.
 */
@Composable
private fun FilterButton(
    filters: ActiveFilters,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    look: IconButtonLook = IconButtonLook.Bare,
) {
    val count = filters.applied.activeCount
    IconButton(
        if (count == 0) "Open filters" else "Open filters, $count active",
        onOpenFilters,
        modifier,
        look = look,
        badgeCount = count,
    ) { Symbol(Res.drawable.symbol_filter_list) }
}

/** The accounts or address whose mail the page shows; a tap opens the account switcher. */
@Composable
private fun InboxAccountStack(
    scope: MailScopeSwitch,
    collapse: () -> Float,
    onOpen: () -> Unit,
) {
    val header = scope.header
    AccountStack(header.avatars, "Showing mail from ${header.title}. Change", onOpen, collapse = collapse)
}

/** The panel while rows are selected: ✕ to stop, the count, and Select all. */
@Composable
private fun SelectionPanel(selecting: PanelSelection) {
    ContextPanel(
        placement = ChipPlacement.None,
        navigation = { PanelNavigation("Stop selecting", selecting.onClear, icon = PanelNavigationIcon.Close) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("${selecting.count} selected", Modifier.weight(1f).padding(start = PanelTokens.titleStart))
            SheetHeaderAction("Select all", selecting.onSelectAll)
        }
    }
}

@Composable
private fun InboxNavigation(
    searching: Boolean,
    onOpenNavigation: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    PanelNavigation(
        if (searching) "Back to inbox" else "Open navigation",
        if (searching) onCloseSearch else onOpenNavigation,
    )
}

/** The search field, focused with the keyboard up while [active]; the user focusing it runs [onFocus]. */
@Composable
internal fun InboxSearchInput(
    query: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    active: Boolean,
    onFocus: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(active) {
        if (active) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    PanelSearchField(
        query,
        onChange,
        "Search your mailbox…",
        Modifier.focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onFocus() },
        onSubmit,
    )
}

/**
 * How the panel looks right now: [collapse] reads how far the list has collapsed it;
 * [unreadCount] shows on the Unread pill; [filters] are the page's active filters, which lead the
 * pill row and count on the filter button; [selecting] is set while rows are selected; and
 * [searchFocused] puts the keyboard in the panel's search field.
 */
class PanelLook(
    val collapse: () -> Float = { 0f },
    val unreadCount: Int = 0,
    val filters: ActiveFilters = ActiveFilters(),
    val selecting: PanelSelection? = null,
    val searchFocused: Boolean = false,
    val expansion: PanelExpansion? = null,
    val expanded: @Composable () -> Unit = {},
)

/**
 * This look with the panel able to grow by [expansion], showing [expanded] in the room it grows,
 * and the keyboard in its search field while [searchFocused].
 */
fun PanelLook.grownWith(
    expansion: PanelExpansion?,
    expanded: @Composable () -> Unit,
    searchFocused: Boolean,
): PanelLook = PanelLook(collapse, unreadCount, filters, selecting, searchFocused, expansion, expanded)

/** The selected rows' [count], with what ✕ and Select all do. */
class PanelSelection(
    val count: Int,
    val onClear: () -> Unit,
    val onSelectAll: () -> Unit,
)
