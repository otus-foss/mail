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
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickStyle
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.designsystem.theme.tokens.people.AvatarTokens
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailboxScope
import com.otus.mail.feature.inbox.domain.selectedAccounts
import com.otus.mail.feature.inbox.domain.shownName
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
    val withMailboxes = allowMailboxSelection
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        when (filter) {
            InboxFilter.People -> peopleChoices(messages, needle, draft, onChange)
            InboxFilter.Accounts -> accountChoices(messages, mailboxSearch, withMailboxes, needle, draft, onChange)
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
    val matches =
        people.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.address.contains(needle, ignoreCase = true)
        }
    if (matches.isEmpty()) {
        item { EmptyChoices(people.isEmpty(), "No senders yet.") }
    }
    items(matches, key = { it.address }) { person ->
        PickRow(
            person.shownName,
            person.address,
            PickMark(person.address in draft.senders, multiple = true, style = PickStyle.Card),
            { onChange(draft.copy(senders = draft.senders.toggled(person.address))) },
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
    val matches =
        accounts.filter {
            it.account.contains(needle, ignoreCase = true) ||
                it.accountAddress.contains(needle, ignoreCase = true)
        }
    // Selected names stay listed so they can be cleared, even when the folder list lacks them.
    val names =
        if (allowMailboxSelection) (mailboxSearch.names + draft.mailboxes.map { it.name }).distinct() else emptyList()
    val nameMatches = names.filter { it.contains(needle, ignoreCase = true) }
    if (matches.isEmpty() && nameMatches.isEmpty()) {
        item { EmptyChoices(accounts.isEmpty() && names.isEmpty(), "No accounts connected.") }
    }
    items(matches, key = { it.accountId }) { account ->
        PickRow(
            account.account,
            // The account name defaults to its address; show the address only when it adds something.
            account.accountAddress.takeIf { it.isNotBlank() && !it.equals(account.account, ignoreCase = true) },
            PickMark(account.accountId in draft.accounts, multiple = true, style = PickStyle.Card),
            { onChange(draft.copy(accounts = draft.accounts.toggled(account.accountId))) },
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
    item { SheetCaption("Inboxes", Modifier.padding(top = SheetTokens.inset)) }
    items(names, key = { "mailbox/$it" }) { name ->
        PickRow(
            name,
            null,
            PickMark(draft.mailboxes.any { it.name == name }, multiple = true, style = PickStyle.Card),
            { onChange(draft.copy(mailboxes = draft.mailboxes.toggledName(name))) },
        )
    }
}

private fun Set<MailboxScope>.toggledName(name: String): Set<MailboxScope> =
    if (any { it.name == name }) filterNot { it.name == name }.toSet() else this + MailboxScope(account = null, name)

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
