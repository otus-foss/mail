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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.SelectionRow
import com.otus.designsystem.theme.AvatarTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailboxScope
import com.otus.mail.feature.inbox.domain.selectedAccounts
import com.otus.mail.feature.inbox.presentation.MailboxSearch

@Composable
internal fun InboxPickerChoices(
    filter: InboxFilter,
    messages: List<MailMessage>,
    mailboxSearch: MailboxSearch,
    allowMailboxSelection: Boolean,
    query: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val needle = query.trim()
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        when (filter) {
            InboxFilter.People -> {
                peopleChoices(messages, needle, draft, onChange)
            }

            InboxFilter.Accounts -> {
                accountChoices(messages, mailboxSearch, allowMailboxSelection, needle, draft, onChange)
            }

            else -> {
                error("This filter does not have searchable choices")
            }
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

/** Accounts, then mailbox names. A name matches that mailbox in every account the search includes. */
private fun LazyListScope.accountChoices(
    messages: List<MailMessage>,
    mailboxSearch: MailboxSearch,
    allowMailboxSelection: Boolean,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val known = mailboxSearch.accounts ?: messages.map { it.location }.distinctBy { it.accountId }
    val accounts =
        selectedAccounts(messages, known.map { it.accountId }.toSet() + draft.accounts, known)
            .sortedBy { it.account }
    val matches = accounts.filter { it.account.contains(needle, true) || it.accountAddress.contains(needle, true) }
    // Selected names stay listed so they can be cleared, even when the folder list lacks them.
    val names =
        if (allowMailboxSelection) (mailboxSearch.names + draft.mailboxes.map { it.name }).distinct() else emptyList()
    val nameMatches = names.filter { it.contains(needle, true) }
    if (matches.isEmpty() && nameMatches.isEmpty()) {
        item { EmptyChoices(accounts.isEmpty() && names.isEmpty(), "No accounts connected.") }
    }
    items(matches, key = { it.accountId }) { account ->
        SelectionRow(
            account.account,
            account.accountId in draft.accounts,
            { onChange(draft.copy(accounts = draft.accounts.toggled(account.accountId))) },
            // The account name defaults to its address; show the address only when it adds something.
            subtitle = account.accountAddress.takeIf { it.isNotBlank() && !it.equals(account.account, true) },
            leading = { AccountAvatar(account) },
        )
    }
    mailboxChoices(nameMatches, draft, onChange)
}

private fun LazyListScope.mailboxChoices(
    names: List<String>,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    if (names.isEmpty()) return
    item { SectionCaption("Inboxes") }
    items(names, key = { "mailbox/$it" }) { name ->
        SelectionRow(
            name,
            draft.mailboxes.any { it.name == name },
            { onChange(draft.copy(mailboxes = draft.mailboxes.toggledName(name))) },
        )
    }
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

private fun Set<MailboxScope>.toggledName(name: String): Set<MailboxScope> =
    if (any { it.name == name }) filterNot { it.name == name }.toSet() else this + MailboxScope(null, name)

@Composable
private fun SectionCaption(text: String) {
    Text(
        text,
        Modifier.padding(top = SheetTokens.inset).semantics { heading() },
        color = OtusColors.text2,
        fontSize = SheetTokens.detailSize,
    )
}

@Composable
private fun AccountAvatar(location: MailLocation) {
    PersonAvatar(chipAvatar(location.accountAvatar, location.account, location.accountAddress), AvatarTokens.size)
}

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
