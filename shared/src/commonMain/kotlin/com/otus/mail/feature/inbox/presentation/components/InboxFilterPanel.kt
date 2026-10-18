package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterPanel
import com.otus.designsystem.components.FilterPanelFooter
import com.otus.designsystem.components.FilterPanelHeader
import com.otus.designsystem.components.InputSheet
import com.otus.designsystem.theme.LocalMotionScale
import com.otus.designsystem.theme.Motion
import com.otus.designsystem.theme.MotionSpeed
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterCount
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.filterSenders
import com.otus.mail.feature.inbox.presentation.showLabel

/**
 * The filter panel of a message list, growing out of its control panel [collapsedHeight] tall.
 * Its main page lists the sections; All folders… slides in the folder tree in place of them.
 */
@Composable
fun InboxFilterPanel(
    state: InboxFilterState,
    options: FilterOptions,
    messages: List<MailMessage>,
    dateFormatter: DateFormatter,
    collapsedHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var folders by rememberSaveable { mutableStateOf(false) }
    var customDate by rememberSaveable { mutableStateOf(false) }
    var addingSender by rememberSaveable { mutableStateOf(false) }
    val close = state::close
    // The panel opens on its main page again, however it closed.
    LaunchedEffect(state.isOpen) { if (state.isOpen) folders = false }
    val draft = state.draft
    // The last draft stays on screen while the panel closes.
    var shown by remember { mutableStateOf(InboxFilters()) }
    if (draft != null) shown = draft
    FilterPanel(
        open = state.isOpen,
        title = "Filters",
        collapsedHeight = collapsedHeight,
        onDismiss = close,
        header = {
            FilterPanelHeader(
                "Filters",
                close,
                closeDescription = "Close filters",
                onBack = if (folders) ({ folders = false }) else null,
            )
        },
        footer = { InboxFilterFooter(state, shown) },
        modifier = modifier,
    ) {
        val scale = LocalMotionScale.current
        AnimatedContent(
            folders,
            contentAlignment = Alignment.TopStart,
            transitionSpec = {
                val sign = if (targetState) 1 else -1
                (
                    slideInHorizontally(Motion.tween(MotionSpeed.Normal, scale = scale)) { it * sign } togetherWith
                        slideOutHorizontally(Motion.tween(MotionSpeed.Normal, scale = scale)) { -it * sign }
                ).using(SizeTransform(clip = true) { _, _ -> Motion.tween(MotionSpeed.Normal, scale = scale) })
            },
            label = "Filter panel page",
        ) { allFolders ->
            if (allFolders) {
                FolderPickerPage(shown, options, state::edit)
            } else {
                val senders = remember(messages, shown.from) { filterSenders(messages, shown.from) }
                val pages = FilterPanelPages({ folders = true }, { customDate = true }, { addingSender = true })
                FilterSections(shown, options, senders, dateFormatter, state::edit, pages)
            }
        }
    }
    if (addingSender && state.isOpen) {
        InputSheet(
            "Add sender",
            "Address",
            "Add",
            onConfirm = { address -> state.edit { it.copy(from = it.from + address.trim()) } },
            onDismiss = { addingSender = false },
            problem = { if ('@' in it.trim()) null else "Enter an email address" },
        )
    }
    if (customDate && state.isOpen) {
        FilterDateSheet(shown, dateFormatter, { range -> state.edit { it.copy(date = range) } }) { customDate = false }
    }
}

@Composable
private fun InboxFilterFooter(
    state: InboxFilterState,
    draft: InboxFilters,
) {
    val count = state.count
    FilterPanelFooter(
        count.showLabel,
        onApply = state::show,
        onReset = { state.edit { InboxFilters() } },
        applyEnabled = count != FilterCount.Known(0),
        resetEnabled = !draft.isDefault,
        counting = count == FilterCount.Counting,
    )
}
