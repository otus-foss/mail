package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.filters.components.FilterPanel
import com.otus.designsystem.shared.filters.components.FilterPanelFooter
import com.otus.designsystem.shared.filters.components.FilterPanelTitle
import com.otus.designsystem.shared.filters.components.FilterResetAction
import com.otus.designsystem.theme.tokens.selection.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterCount
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.showLabel

/**
 * The filter panel of a message list, growing out of its control panel [collapsedHeight] tall
 * under the page's [title]. Every section sits on one scrolling page, and Account, Folders, and
 * Date open popups over it. Back, the chevron, a tap on the scrim, a swipe up, and Show close it,
 * and it keeps one height.
 */
@Composable
fun InboxFilterPanel(
    state: InboxFilterState,
    options: FilterOptions,
    messages: List<MailMessage>,
    dateFormatter: DateFormatter,
    collapsedHeight: Dp,
    title: String,
    modifier: Modifier = Modifier,
) {
    // The last draft stays on screen while the panel closes.
    var shown by remember { mutableStateOf(InboxFilters()) }
    val draft = state.draft
    if (draft != null) shown = draft
    val people = rememberFilterPeople(state, options, messages)
    FilterPanel(
        open = state.isOpen,
        collapsedHeight = collapsedHeight,
        onDismiss = state::close,
        header = {
            FilterPanelTitle(
                title,
                state::close,
            ) { FilterResetAction(state::reset, enabled = !shown.isDefault) }
        },
        footer = { InboxFilterFooter(state) },
        modifier = modifier,
    ) {
        val searches = PeopleSearches(state.senders, state.recipients, people)
        FilterSections(shown, options, dateFormatter, state::edit, searches)
    }
}

@Composable
private fun InboxFilterFooter(state: InboxFilterState) {
    val count = state.count
    FilterPanelFooter(
        count.showLabel,
        onApply = state::show,
        applyEnabled = count != FilterCount.Known(0),
        counting = count == FilterCount.Counting,
    )
}
