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
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_person
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailSender
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun SearchFilterChip(
    filter: InboxFilter,
    search: InboxSearch,
    defaultSearch: InboxSearch,
    dateFormatter: DateFormatter,
    senders: List<MailSender>,
    onClick: () -> Unit,
) {
    if (filter == InboxFilter.People && senders.isNotEmpty()) {
        PeopleFilterChip(senders, onClick)
        return
    }
    // The page's default mailbox scope and every account cover everything, so they read "All" without a highlight.
    val coversAll =
        when (filter) {
            InboxFilter.Mailboxes -> search.mailboxes == defaultSearch.mailboxes
            InboxFilter.Accounts -> search.accounts.isEmpty()
            else -> false
        }
    val selected =
        when (filter) {
            InboxFilter.People -> search.senders.isNotEmpty()
            InboxFilter.Date -> search.dateStart != null
            InboxFilter.Mailboxes, InboxFilter.Accounts -> !coversAll
            InboxFilter.More -> search.unreadOnly || search.attachmentsOnly
        }
    val icon = filterIcon(filter)
    if (coversAll) {
        val description = "All ${filter.title.lowercase()}"
        Chip("All", onClick, Modifier.semantics { contentDescription = description }, selected = selected) {
            Symbol(icon)
        }
        return
    }
    val label = filterLabel(filter, search, selected, dateFormatter)
    Chip(label, onClick, selected = selected) { Symbol(icon) }
}

private fun filterIcon(filter: InboxFilter): DrawableResource =
    when (filter) {
        InboxFilter.People -> Res.drawable.symbol_person
        InboxFilter.Date -> Res.drawable.symbol_calendar_add_on
        InboxFilter.Mailboxes -> Res.drawable.symbol_inbox
        else -> Res.drawable.symbol_alternate_email
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

        InboxFilter.Mailboxes -> {
            val count = if (search.mailboxes.isEmpty()) "All" else search.mailboxes.size.toString()
            "Inboxes · $count"
        }

        InboxFilter.Accounts -> {
            "Accounts · ${search.accounts.size}"
        }

        InboxFilter.More -> {
            filter.title
        }
    }
