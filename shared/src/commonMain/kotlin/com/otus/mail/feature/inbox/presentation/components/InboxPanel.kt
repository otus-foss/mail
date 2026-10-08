package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
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
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.designsystem.components.ChipPlacement
import com.otus.mail.designsystem.components.ContextPanel
import com.otus.mail.designsystem.elements.IconButton
import com.otus.mail.designsystem.elements.SearchField
import com.otus.mail.designsystem.elements.SearchPrompt
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_chevron_left
import com.otus.mail.designsystem.resources.symbol_close
import com.otus.mail.designsystem.resources.symbol_filter_list
import com.otus.mail.designsystem.theme.PanelTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.InboxContext

@Composable
fun InboxPanel(
    searching: Boolean,
    search: InboxSearch,
    dateFormatter: DateFormatter,
    context: InboxContext,
    actions: InboxPanelActions,
    selectedSenders: List<MailSender> = emptyList(),
) {
    val onChange = actions.onChange
    val onSearchingChange = actions.onSearchingChange
    val onFilter = actions.onFilter
    val onSubmit = actions.onSubmit
    val onOpenNavigation = actions.onOpenNavigation
    ContextPanel(
        placement = if (searching) ChipPlacement.Overlapping else ChipPlacement.Below,
        navigation = { InboxNavigation(searching, onOpenNavigation) { onSearchingChange(false) } },
        trailingAction = {
            IconButton("Open filters", { onFilter(InboxFilter.More) }) {
                Symbol(Res.drawable.symbol_filter_list)
            }
        },
        chips = {
            InboxPanelChips(
                searching,
                search,
                context.defaultSearch,
                dateFormatter,
                selectedSenders,
                onChange,
                onFilter,
            )
        },
    ) {
        AnimatedContent(
            targetState = searching,
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                (
                    fadeIn(tween(PanelTokens.MODE_TRANSITION_MILLIS)) togetherWith
                        fadeOut(tween(PanelTokens.MODE_TRANSITION_MILLIS))
                ).using(SizeTransform(clip = false) { _, _ -> tween(PanelTokens.MODE_TRANSITION_MILLIS) })
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
