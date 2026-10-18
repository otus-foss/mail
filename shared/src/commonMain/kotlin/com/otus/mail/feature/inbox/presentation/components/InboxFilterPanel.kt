package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterPanel
import com.otus.designsystem.components.FilterPanelFooter
import com.otus.designsystem.components.FilterPanelHeader
import com.otus.designsystem.components.SlidingPages
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.FilterCount
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPage
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.showLabel

/**
 * The filter panel of a message list, growing out of its control panel [collapsedHeight] tall.
 * Its main page lists the sections, and its rows and People lines slide in a page of their own.
 * Every page edits the same draft above the same footer, and the panel keeps one height.
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
    val close = state::close
    val page = state.page
    // The last draft stays on screen while the panel closes.
    var shown by remember { mutableStateOf(InboxFilters()) }
    state.draft?.let { shown = it }
    // The page the user last opened, whose row on the main page takes focus when they come back.
    var opened by remember { mutableStateOf<FilterPage?>(null) }
    LaunchedEffect(page, state.isOpen) {
        opened =
            when {
                !state.isOpen -> null
                page != FilterPage.Main -> page
                else -> opened
            }
    }
    FilterPanel(
        open = state.isOpen,
        collapsedHeight = collapsedHeight,
        onDismiss = close,
        header = {
            FilterPanelHeader(
                page.title,
                close,
                closeDescription = "Close filters",
                onBack = if (page == FilterPage.Main) null else state::back,
            )
        },
        footer = { InboxFilterFooter(state, shown) },
        modifier = modifier,
        fillHeight = true,
    ) {
        SlidingPages(page, depth = { if (it == FilterPage.Main) 0 else 1 }) { shownPage ->
            FilterPageContent(shownPage, shown, state, options, messages, dateFormatter, opened)
        }
    }
    // Composed only on a sub-page, so it registers after the panel's own handler and runs first.
    if (state.isOpen && page != FilterPage.Main) BackHandler(enabled = true, state::back)
}

@Composable
private fun FilterPageContent(
    page: FilterPage,
    draft: InboxFilters,
    state: InboxFilterState,
    options: FilterOptions,
    messages: List<MailMessage>,
    dateFormatter: DateFormatter,
    returnedFrom: FilterPage?,
) {
    val people = rememberFilterPeople(state, options, messages)
    when (page) {
        FilterPage.Main -> {
            val links =
                MainPageLinks(
                    pickedSenders(draft, people.senders),
                    pickedRecipients(draft, options, people.recipients),
                    state::openPage,
                    returnedFrom,
                )
            FilterSections(draft, options, dateFormatter, state::edit, links)
        }

        FilterPage.From -> {
            FromPage(draft, state.senders, people.senders, state::edit)
        }

        FilterPage.To -> {
            ToPage(draft, state.recipients, people.recipients, state::edit)
        }

        FilterPage.Account -> {
            AccountPage(draft, options, state::edit)
        }

        FilterPage.Folders -> {
            FolderPickerPage(draft, options, state::edit)
        }

        FilterPage.Date -> {
            DatePage(draft.date, state::edit)
        }

        FilterPage.Metadata -> {
            MetadataPage(draft, state::edit)
        }
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
        onReset = state::reset,
        applyEnabled = count != FilterCount.Known(0),
        resetEnabled = !draft.isDefault,
        counting = count == FilterCount.Counting,
    )
}

/** The page's name in the panel's header. */
private val FilterPage.title: String
    get() =
        when (this) {
            FilterPage.Main -> "Filters"
            FilterPage.From -> "From"
            FilterPage.To -> "To"
            FilterPage.Account -> "Account"
            FilterPage.Folders -> "Folders"
            FilterPage.Date -> "Date"
            FilterPage.Metadata -> "Metadata"
        }
