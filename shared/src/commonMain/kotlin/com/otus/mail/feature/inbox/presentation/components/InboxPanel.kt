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
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.IconButton
import com.otus.designsystem.elements.SearchField
import com.otus.designsystem.elements.SearchPrompt
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_chevron_left
import com.otus.designsystem.resources.symbol_close
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.theme.LocalMotionScale
import com.otus.designsystem.theme.Motion
import com.otus.designsystem.theme.PanelTokens
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
    val onChange = actions.onChange
    val onSearchingChange = actions.onSearchingChange
    val onFilter = actions.onFilter
    val onSubmit = actions.onSubmit
    val onOpenNavigation = actions.onOpenNavigation
    ContextPanel(
        // Without pills, the filter button moves into the header and the list gets the row's space.
        placement =
            when {
                searching || compact -> ChipPlacement.Overlapping
                context.pills.shown.isEmpty() -> ChipPlacement.None
                else -> ChipPlacement.Below
            },
        navigation = { InboxNavigation(searching, onOpenNavigation) { onSearchingChange(false) } },
        trailingAction = {
            IconButton("Open filters", { onFilter(InboxFilter.More) }) {
                Symbol(Res.drawable.symbol_filter_list)
            }
        },
        chips = { InboxPanelChips(searching, search, context, dateFormatter, selection, actions, look.unreadCount) },
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
                InboxSearchInput(search.query, { onChange(search.copy(query = it)) }, onSubmit, active = searching)
            } else {
                SearchPrompt(context.searchPrompt, onClick = { onSearchingChange(true) })
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
    if (searching) {
        IconButton("Back to inbox", onCloseSearch, outlined = false) {
            Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
        }
    } else {
        IconButton("Open navigation", onOpenNavigation, outlined = false) {
            Symbol(Res.drawable.symbol_chevron_left, mirrorInRtl = true, size = PanelTokens.navigationIconSize)
        }
    }
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
            IconButton("Clear search", { onChange("") }, outlined = false) { Symbol(Res.drawable.symbol_close) }
        }
    }
}

/** A [compact] panel is shorter, as while its list scrolls down; [unreadCount] shows on the Unread pill. */
data class PanelLook(
    val compact: Boolean = false,
    val unreadCount: Int = 0,
)
