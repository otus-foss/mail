package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.currentLocalDate
import com.otus.designsystem.components.DateRangePicker
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.components.SelectionSheet
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
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
            InboxFilter.Date -> {
                DateRangePicker(draft.dateStart, draft.dateEnd, dateFormatter) { start, end ->
                    draft = draft.copy(dateStart = start, dateEnd = end)
                }
            }

            InboxFilter.People, InboxFilter.Accounts -> {
                Text(
                    "${draft.selectionCount(filter)} selected",
                    color = OtusColors.text2,
                    fontSize = SheetTokens.detailSize,
                )
                InboxPickerChoices(
                    filter,
                    messages,
                    context.mailboxSearch,
                    context.allowsMailboxSelection,
                    query,
                    draft,
                ) { draft = it }
            }
        }
    }
}

/** No date range, or one with both ends no later than [today]. */
private fun InboxSearch.hasCompleteDates(today: String): Boolean {
    val first = dateStart
    val last = dateEnd
    return first == null || (last != null && first <= today && last <= today)
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
