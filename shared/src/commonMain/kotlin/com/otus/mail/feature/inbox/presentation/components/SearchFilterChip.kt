package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_alternate_email
import com.otus.designsystem.resources.symbol_calendar_add_on
import com.otus.designsystem.resources.symbol_person
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.presentation.InboxContext
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun SearchFilterChip(
    filter: InboxFilter,
    search: InboxSearch,
    context: InboxContext,
    dateFormatter: DateFormatter,
    selection: FilterSelection,
    onClick: () -> Unit,
) {
    if (filter == InboxFilter.People && selection.senders.isNotEmpty()) {
        PeopleFilterChip(selection.senders, onClick)
        return
    }
    val mailboxes =
        if (filter == InboxFilter.Accounts && context.allowsMailboxSelection) {
            MailboxSummary.of(search, context.defaultSearch)
        } else {
            null
        }
    if (filter == InboxFilter.Accounts && selection.accounts.isNotEmpty()) {
        AccountsFilterChip(selection.accounts, mailboxes, onClick)
        return
    }
    val selected =
        when (filter) {
            InboxFilter.People -> {
                search.senders.isNotEmpty()
            }

            InboxFilter.Date -> {
                search.dateStart != null
            }

            InboxFilter.Accounts -> {
                search.accounts.isNotEmpty() ||
                    (context.allowsMailboxSelection && search.mailboxes != context.defaultSearch.mailboxes)
            }
        }
    val label = filterLabel(filter, search, selected, dateFormatter)
    Chip(
        label,
        onClick,
        Modifier.mailboxDescription(label, mailboxes),
        selected = selected,
        detail = mailboxes?.label,
    ) { Symbol(filterIcon(filter)) }
}

/** Spoken in place of the divider line, such as "All accounts, Inbox, Archive". */
private fun Modifier.mailboxDescription(
    label: String,
    mailboxes: MailboxSummary?,
): Modifier = if (mailboxes == null) this else semantics { contentDescription = "$label, ${mailboxes.description}" }

private fun filterIcon(filter: InboxFilter): DrawableResource =
    when (filter) {
        InboxFilter.People -> Res.drawable.symbol_person
        InboxFilter.Date -> Res.drawable.symbol_calendar_add_on
        InboxFilter.Accounts -> Res.drawable.symbol_alternate_email
    }

private fun filterLabel(
    filter: InboxFilter,
    search: InboxSearch,
    selected: Boolean,
    dateFormatter: DateFormatter,
): String =
    when (filter) {
        InboxFilter.People -> {
            if (selected) "From · ${search.senders.size}" else filter.title
        }

        InboxFilter.Date -> {
            dateFilterLabel(search, dateFormatter)
        }

        InboxFilter.Accounts -> {
            if (search.accounts.isNotEmpty()) "Accounts · ${search.accounts.size}" else "All accounts"
        }
    }
