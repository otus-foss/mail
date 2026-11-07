package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.overlays.components.SheetFootnote
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.theme.tokens.people.AvatarTokens
import com.otus.mail.core.mail.EmailAddressKey
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.shownName
import com.otus.mail.core.messages.presentation.components.chipAvatar
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.selectedAccounts
import com.otus.mail.feature.inbox.presentation.InboxContext

/** The senders of the loaded [messages] whose name or address holds [needle], each to tick. */
@Composable
internal fun PeopleChoices(
    messages: List<MailMessage>,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val people =
        messages
            .map { it.sender }
            .distinctBy { EmailAddressKey(it.address) }
            .sortedBy { it.shownName.lowercase() }
    val matches = people.filter { it.name.contains(needle, true) || it.address.contains(needle, true) }
    if (matches.isEmpty()) NoChoices(people.isEmpty(), "No senders yet")
    matches.forEach { person ->
        PickRow(
            person.shownName,
            person.address.takeIf { person.name.isNotBlank() },
            PickMark(person.address in draft.senders, multiple = true),
            { onChange(draft.copy(senders = draft.senders.toggled(person.address))) },
        ) { PersonAvatar(person.chipAvatar, AvatarTokens.size) }
    }
}

/**
 * The [page]'s accounts, including those without loaded mail, then its inboxes when the page lets a
 * search pick them. An inbox counts in every account the search includes.
 */
@Composable
internal fun AccountChoices(
    page: InboxContext,
    messages: List<MailMessage>,
    needle: String,
    draft: InboxSearch,
    onChange: (InboxSearch) -> Unit,
) {
    val known = page.mailboxSearch.accounts ?: messages.map { it.location }.distinctBy { it.accountId }
    val accounts =
        selectedAccounts(messages, known.map { it.accountId }.toSet() + draft.accounts, known).sortedBy { it.account }
    val matches = accounts.filter { it.account.contains(needle, true) || it.accountAddress.contains(needle, true) }
    // Picked inboxes stay listed so they can be unticked, even when the folder list lacks them.
    val mailboxes =
        if (page.allowsMailboxSelection) (page.mailboxSearch.mailboxes + draft.mailboxes).distinct() else emptyList()
    val mailboxMatches = mailboxes.filter { it.name.contains(needle, true) }
    if (matches.isEmpty() && mailboxMatches.isEmpty()) {
        NoChoices(accounts.isEmpty() && mailboxes.isEmpty(), "No accounts connected")
    }
    matches.forEach { account ->
        PickRow(
            account.account,
            // An account's name defaults to its address, so the address shows only when it adds to it.
            account.accountAddress.takeIf { it.isNotBlank() && !it.equals(account.account, true) },
            PickMark(account.accountId in draft.accounts, multiple = true),
            { onChange(draft.copy(accounts = draft.accounts.toggled(account.accountId))) },
        ) {
            PersonAvatar(
                chipAvatar(account.accountAvatar, account.account, account.accountAddress),
                AvatarTokens.size,
            )
        }
    }
    if (mailboxMatches.isNotEmpty()) SectionCaption("Inboxes")
    mailboxMatches.forEach { mailbox ->
        PickRow(
            mailbox.name,
            null,
            PickMark(mailbox in draft.mailboxes, multiple = true),
            { onChange(draft.copy(mailboxes = draft.mailboxes.toggled(mailbox))) },
        )
    }
}

/** Why a list has no rows: nothing to pick from at all, else nothing matching the typed text. */
@Composable
private fun NoChoices(
    nothing: Boolean,
    nothingText: String,
) = SheetFootnote(if (nothing) nothingText else "No matches")
