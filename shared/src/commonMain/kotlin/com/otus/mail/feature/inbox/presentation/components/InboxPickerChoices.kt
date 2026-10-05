package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.mail.designsystem.elements.SelectionRow
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailboxScope

@Composable
internal fun InboxPickerChoices(
    filter: InboxFilter,
    messages: List<MailMessage>,
    query: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val needle = query.trim()
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        when (filter) {
            InboxFilter.People -> peopleChoices(messages, needle, draft, onChange)
            InboxFilter.Mailboxes -> mailboxChoices(messages, needle, draft, onChange)
            InboxFilter.Accounts -> accountChoices(messages, needle, draft, onChange)
            else -> error("This filter does not have searchable choices")
        }
    }
}

private fun LazyListScope.peopleChoices(
    messages: List<MailMessage>,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val people = messages.map { it.sender }.distinctBy { it.address }.sortedBy { it.name.lowercase() }
    val matches = people.filter { it.name.contains(needle, true) || it.address.contains(needle, true) }
    if (matches.isEmpty()) {
        item { EmptyChoices(people.isEmpty(), "No senders yet. Your mail contacts will appear here.") }
    }
    items(matches, key = { it.address }) { person ->
        SelectionRow(
            person.name.ifBlank { person.address },
            person.address in draft.senders,
            { onChange(draft.copy(senders = draft.senders.toggled(person.address))) },
            subtitle = person.address,
            leading = {
                SenderAvatar(person)
            },
        )
    }
}

private fun LazyListScope.mailboxChoices(
    messages: List<MailMessage>,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val accounts = messages.associate { it.location.accountId to it.location }
    val mailboxes =
        messages
            .map { MailboxScope(it.location.accountId, it.location.mailbox) }
            .distinct()
            .sortedWith(compareBy({ it.account }, { it.name }))
    val groups =
        mailboxes
            .filter { it.name.contains(needle, true) || accounts[it.account]?.account.orEmpty().contains(needle, true) }
            .groupBy { it.account }
    val expanded =
        mailboxes
            .filter { option ->
                draft.mailboxes.any { it.name == option.name && (it.account == null || it.account == option.account) }
            }.toSet()
    if (groups.isEmpty()) item { EmptyChoices(mailboxes.isEmpty(), "No inboxes available yet.") }
    groups.forEach { (account, choices) ->
        item {
            val location = accounts[account]
            val label =
                listOfNotNull(
                    location?.account,
                    location?.accountDetail?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
            Text(label, Modifier.padding(top = SheetTokens.gap), color = OtusColors.text2)
        }
        items(choices) { scope ->
            SelectionRow(scope.name, scope in expanded, { onChange(draft.copy(mailboxes = expanded.toggled(scope))) })
        }
    }
}

private fun LazyListScope.accountChoices(
    messages: List<MailMessage>,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val accounts = messages.map { it.location }.distinctBy { it.accountId }.sortedBy { it.account }
    val matches = accounts.filter { it.account.contains(needle, true) || it.accountDetail.contains(needle, true) }
    if (matches.isEmpty()) item { EmptyChoices(accounts.isEmpty(), "No accounts connected.") }
    items(matches, key = { it.accountId }) { account ->
        SelectionRow(
            account.account,
            account.accountId in draft.accounts,
            { onChange(draft.copy(accounts = draft.accounts.toggled(account.accountId))) },
            subtitle = account.accountDetail.takeIf { it.isNotBlank() },
        )
    }
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun EmptyChoices(
    noSource: Boolean,
    emptyMessage: String,
) {
    Text(
        if (noSource) emptyMessage else "No matches. Try another search.",
        Modifier.padding(vertical = SheetTokens.inset),
        color = OtusColors.text2,
    )
}
