package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.core.dateformat.currentLocalDate
import com.otus.mail.designsystem.components.PickerSearch
import com.otus.mail.designsystem.components.SelectionSheet
import com.otus.mail.designsystem.elements.SelectionRow
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.presentation.InboxSearchSaver

@Composable
fun InboxFilterSheet(
    filter: InboxFilter,
    messages: List<MailMessage>,
    search: InboxSearch,
    dateFormatter: DateFormatter,
    onApply: (InboxSearch) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(filter, stateSaver = InboxSearchSaver) { mutableStateOf(search) }
    var query by rememberSaveable(filter) { mutableStateOf("") }
    val today = currentLocalDate().toString()
    val firstDate = draft.dateStart
    val lastDate = draft.dateEnd
    val validDateRange = firstDate == null || (lastDate != null && firstDate <= today && lastDate <= today)
    val searchable = filter in listOf(InboxFilter.People, InboxFilter.Mailboxes, InboxFilter.Accounts)
    SelectionSheet(
        title = if (filter == InboxFilter.People) "From" else filter.title,
        onDismiss = onDismiss,
        onClear = { draft = draft.clear(filter) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
        applyEnabled = filter != InboxFilter.Date || validDateRange,
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

            InboxFilter.More -> {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
                ) {
                    SelectionRow(
                        title = "Unread only",
                        selected = draft.unreadOnly,
                        onClick = { draft = draft.copy(unreadOnly = !draft.unreadOnly) },
                    )
                    SelectionRow(
                        title = "Has attachments",
                        selected = draft.attachmentsOnly,
                        onClick = { draft = draft.copy(attachmentsOnly = !draft.attachmentsOnly) },
                    )
                }
            }

            else -> {
                Text(
                    "${draft.selectionCount(filter, messages)} selected",
                    color = OtusColors.text2,
                    fontSize = SheetTokens.detailSize,
                )
                InboxPickerChoices(filter, messages, query, draft) { draft = it }
            }
        }
    }
}

private fun InboxSearch.clear(filter: InboxFilter): InboxSearch =
    when (filter) {
        InboxFilter.People -> copy(senders = emptySet())
        InboxFilter.Date -> copy(dateStart = null, dateEnd = null)
        InboxFilter.Mailboxes -> copy(mailboxes = emptySet())
        InboxFilter.Accounts -> copy(accounts = emptySet())
        InboxFilter.More -> copy(unreadOnly = false, attachmentsOnly = false)
    }

private fun InboxSearch.selectionCount(
    filter: InboxFilter,
    messages: List<MailMessage>,
): Int =
    when (filter) {
        InboxFilter.People -> {
            senders.size
        }

        InboxFilter.Mailboxes -> {
            messages.map { it.location }.distinctBy { it.accountId to it.mailbox }.count { location ->
                mailboxes.any { it.matches(location) }
            }
        }

        InboxFilter.Accounts -> {
            accounts.size
        }

        else -> {
            0
        }
    }
