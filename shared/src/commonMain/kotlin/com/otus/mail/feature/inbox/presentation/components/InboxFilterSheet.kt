package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.shared.dates.components.DateRangePicker
import com.otus.designsystem.shared.overlays.components.SelectionSheet
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxSearchSaver

@Composable
fun InboxFilterSheet(
    filter: InboxFilter,
    messages: List<MailMessage>,
    search: InboxSearch,
    context: InboxContext,
    dateFormatter: DateFormatter,
    onApply: (InboxSearch) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(filter, stateSaver = InboxSearchSaver) { mutableStateOf(search) }
    var query by rememberSaveable(filter) { mutableStateOf("") }
    val updateDraft = { changed: InboxSearch -> draft = changed }
    val searchable = filter in listOf(InboxFilter.People, InboxFilter.Accounts)
    SelectionSheet(
        title = if (filter == InboxFilter.People) "From" else filter.title,
        onDismiss = onDismiss,
        onClear = { draft = draft.clear(filter) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
        applyEnabled = filter != InboxFilter.Date || draft.hasCompleteDates(currentLocalDate().toString()),
        search =
            if (searchable) {
                { PickerSearch(query, { query = it }, "Search ${filter.title.lowercase()}…") }
            } else {
                null
            },
    ) {
        when (filter) {
            InboxFilter.Date -> SearchDates(draft, dateFormatter, updateDraft)
            InboxFilter.People, InboxFilter.Accounts -> context.Choices(filter, messages, query, draft, updateDraft)
        }
    }
}

@Composable
private fun SearchDates(
    draft: InboxSearch,
    dateFormatter: DateFormatter,
    onChange: (InboxSearch) -> Unit,
) {
    DateRangePicker(draft.dateStart, draft.dateEnd, dateFormatter) { start, end ->
        onChange(draft.copy(dateStart = start, dateEnd = end))
    }
}

@Composable
private fun InboxContext.Choices(
    filter: InboxFilter,
    messages: List<MailMessage>,
    query: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    SheetCaption("${draft.selectionCount(filter)} selected", heading = false)
    InboxPickerChoices(filter, messages, mailboxSearch, allowsMailboxSelection, query, draft, onChange)
}

/** No date range, or one with both ends no later than [today]. */
private fun InboxSearch.hasCompleteDates(today: String): Boolean {
    val first = dateStart ?: return true
    val last = dateEnd
    return last != null && first <= today && last <= today
}

/** Unchecks everything in [filter]; nothing checked means all of it. */
private fun InboxSearch.clear(filter: InboxFilter): InboxSearch =
    when (filter) {
        InboxFilter.People -> copy(senders = emptySet())
        InboxFilter.Date -> copy(dateStart = null, dateEnd = null)
        InboxFilter.Accounts -> copy(accounts = emptySet(), mailboxes = emptySet())
    }

private fun InboxSearch.selectionCount(filter: InboxFilter): Int =
    when (filter) {
        InboxFilter.People -> senders.size
        InboxFilter.Accounts -> accounts.size + mailboxes.size
        else -> 0
    }
