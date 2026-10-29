package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.controls.elements.IconButtonLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.input.elements.SearchField
import com.otus.designsystem.shared.input.elements.SearchPrompt
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.theme.motion.LocalMotionScale
import com.otus.designsystem.theme.motion.Motion
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext

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
    val compact = look.compact
    val filters = look.filters
    ContextPanel(
        // Without pills, the filter button moves into the header and the list gets the row's space.
        placement =
            when {
                searching || compact -> ChipPlacement.Overlapping
                context.pills.shown.isEmpty() -> ChipPlacement.None
                else -> ChipPlacement.Below
            },
        navigation = { InboxNavigation(searching, actions.onOpenNavigation) { actions.onSearchingChange(false) } },
        trailingAction = {
            val count = filters.applied.activeCount
            IconButton(
                if (count == 0) "Open filters" else "Open filters, $count active",
                actions.onOpenFilters,
                Modifier.focusRequester(filters.button),
                badgeCount = count,
            ) { Symbol(Res.drawable.symbol_filter_list) }
        },
        chips = { InboxPanelChips(searching, search, context, dateFormatter, selection, actions, look) },
        // Scrolled down, the pills join a shorter header as in search, so the list gets more room.
        compact = compact && !searching,
    ) {
        val motionScale = LocalMotionScale.current
        AnimatedContent(
            targetState = searching,
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
                    actions.onSubmit,
                    active = searching,
                )
            } else {
                SearchPrompt(context.searchPrompt, onClick = { actions.onSearchingChange(true) })
            }
        }
    }
}

@Composable
private fun InboxNavigation(
    searching: Boolean,
    onOpenNavigation: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    IconButton(
        if (searching) "Back to inbox" else "Open navigation",
        if (searching) onCloseSearch else onOpenNavigation,
        look = IconButtonLook.Plain,
    ) { Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize) }
}

@Composable
private fun InboxSearchInput(
    query: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    active: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(active) {
        if (active) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SearchField(
            query,
            onChange,
            "Search your mailbox…",
            Modifier.weight(1f).focusRequester(focusRequester),
            onSubmit,
        )
        if (query.isNotEmpty()) {
            IconButton(
                "Clear search",
                { onChange("") },
                look = IconButtonLook.Plain,
            ) { Symbol(Res.drawable.symbol_close) }
        }
    }
}

/**
 * How the panel looks right now: a [compact] panel is shorter, as while its list scrolls down;
 * [unreadCount] shows on the Unread pill; [filters] are the page's active filters, which lead the
 * pill row and count on the filter button.
 */
class PanelLook(
    val compact: Boolean = false,
    val unreadCount: Int = 0,
    val filters: ActiveFilters = ActiveFilters(),
)
