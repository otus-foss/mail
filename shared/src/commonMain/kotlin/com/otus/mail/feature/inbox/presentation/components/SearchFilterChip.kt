package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.core.dateformat.currentLocalDate
import com.otus.mail.core.dateformat.formatRelative
import com.otus.mail.core.dateformat.formatRelativeRange
import com.otus.mail.designsystem.elements.Chip
import com.otus.mail.designsystem.elements.Symbol
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_alternate_email
import com.otus.mail.designsystem.resources.symbol_calendar_add_on
import com.otus.mail.designsystem.resources.symbol_inbox
import com.otus.mail.designsystem.resources.symbol_person
import com.otus.mail.feature.inbox.domain.InboxSearch
import kotlinx.datetime.LocalDate

@Composable
fun SearchFilterChip(
    filter: InboxFilter,
    search: InboxSearch,
    dateFormatter: DateFormatter,
    onClick: () -> Unit,
) {
    val selected =
        when (filter) {
            InboxFilter.People -> search.senders.isNotEmpty()
            InboxFilter.Date -> search.dateStart != null
            InboxFilter.Mailboxes -> search.mailboxes != InboxSearch().mailboxes
            InboxFilter.Accounts -> search.accounts.isNotEmpty()
            InboxFilter.More -> search.unreadOnly || search.attachmentsOnly
        }
    val icon =
        when (filter) {
            InboxFilter.People -> Res.drawable.symbol_person
            InboxFilter.Date -> Res.drawable.symbol_calendar_add_on
            InboxFilter.Mailboxes -> Res.drawable.symbol_inbox
            else -> Res.drawable.symbol_alternate_email
        }
    val label = filterLabel(filter, search, selected, dateFormatter)
    Chip(label, onClick, selected = selected) { Symbol(icon) }
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
            val start = search.dateStart
            if (start != null) {
                val first = LocalDate.parse(start)
                val last = LocalDate.parse(search.dateEnd ?: start)
                val today = currentLocalDate()
                if (first == last) {
                    dateFormatter.formatRelative(first, today)
                } else {
                    dateFormatter.formatRelativeRange(first, last, today, separator = "–")
                }
            } else {
                filter.title
            }
        }

        InboxFilter.Mailboxes -> {
            val count = if (search.mailboxes.isEmpty()) "All" else search.mailboxes.size.toString()
            if (selected) "Inboxes · $count" else filter.title
        }

        InboxFilter.Accounts -> {
            if (selected) "Accounts · ${search.accounts.size}" else filter.title
        }

        InboxFilter.More -> {
            filter.title
        }
    }
