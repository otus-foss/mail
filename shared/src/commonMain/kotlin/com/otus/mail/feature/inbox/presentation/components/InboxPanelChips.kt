package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.ContextPanelScope
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_mark_email_unread
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailSender

@Composable
internal fun ContextPanelScope.InboxPanelChips(
    searching: Boolean,
    search: InboxSearch,
    defaultSearch: InboxSearch,
    dateFormatter: DateFormatter,
    senders: List<MailSender>,
    onChange: (InboxSearch) -> Unit,
    onFilter: (InboxFilter) -> Unit,
) {
    transition(searching) { mode ->
        if (mode) {
            InboxFilter.entries.filter { it != InboxFilter.More }.forEach { filter ->
                item(
                    filter,
                ) { SearchFilterChip(filter, search, defaultSearch, dateFormatter, senders) { onFilter(filter) } }
            }
        } else {
            item("unread") {
                Chip(
                    "Unread",
                    { onChange(search.copy(unreadOnly = !search.unreadOnly)) },
                    selected = search.unreadOnly,
                ) { Symbol(Res.drawable.symbol_mark_email_unread) }
            }
            item("attachments") {
                Chip(
                    "Attachments",
                    { onChange(search.copy(attachmentsOnly = !search.attachmentsOnly)) },
                    selected = search.attachmentsOnly,
                ) { Symbol(Res.drawable.symbol_attach_file) }
            }
        }
    }
}
