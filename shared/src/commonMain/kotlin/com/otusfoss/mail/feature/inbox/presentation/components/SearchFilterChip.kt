package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_alternate_email
import com.otusfoss.core.designsystem.resources.symbol_calendar_add_on
import com.otusfoss.core.designsystem.resources.symbol_person
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.ChipLabel
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.presentation.label
import com.otusfoss.mail.feature.inbox.domain.InboxSearch
import com.otusfoss.mail.feature.inbox.presentation.InboxContext
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
        ChipLabel(label, detail = mailboxes?.label),
        onClick,
        Modifier.mailboxDescription(label, mailboxes),
        selected = selected,
    ) { Symbol(filterIcon(filter)) }
}

/** Spoken in place of the divider line, such as "All accounts, Inbox, Archive". */
private fun Modifier.mailboxDescription(
    label: String,
    mailboxes: MailboxSummary?,
): Modifier = mailboxes?.let { semantics { contentDescription = "$label, ${it.description}" } } ?: this

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
