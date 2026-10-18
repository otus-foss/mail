package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.ChipLabel
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPerson
import com.otus.mail.feature.inbox.presentation.hasSender
import com.otus.mail.feature.inbox.presentation.withSender

/** Senders to filter by: the most frequent in the list, each with every address they use. */
@Composable
internal fun FromSection(
    draft: InboxFilters,
    senders: List<FilterPerson>,
    onEdit: FilterEdit,
) {
    FilterSection("From") {
        senders.forEach { person ->
            val on = draft.hasSender(person)
            val spoken = (listOf(person.name) + person.addresses).filter(String::isNotBlank).joinToString(", ")
            FilterChip(ChipLabel(person.label, spoken = spoken), on, { onEdit { it.withSender(person, !on) } }) {
                PersonAvatar(
                    chipAvatar(person.avatar, person.name, person.addresses.first()),
                    FilterTokens.chipAvatarSize,
                )
            }
        }
    }
}

/** The page's accounts, while it shows more than one. */
@Composable
internal fun AccountsSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    FilterSection("Account") {
        options.accounts.forEach { account ->
            val on = account.id == draft.account
            FilterChip(account.name, on, {
                onEdit { it.copy(account = account.id.takeUnless { on }) }
            }, role = FilterChipRole.Radio) { PersonAvatar(account.avatar, FilterTokens.chipAvatarSize) }
        }
    }
}

@Composable
internal fun SentToSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    FilterSection("Sent to") {
        options.addresses.forEach { (address, avatar) ->
            val on = address in draft.sentTo
            FilterChip(address.address, on, {
                onEdit { it.copy(sentTo = if (on) it.sentTo - address else it.sentTo + address) }
            }) { PersonAvatar(avatar, FilterTokens.chipAvatarSize) }
        }
    }
}

/** The fields the chosen recipients are looked up in: To, Cc, or Bcc. */
@Composable
internal fun ReceivedInSection(
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    FilterSection("Received in") {
        RecipientField.entries.forEach { field ->
            val on = field in draft.receivedIn
            FilterChip(field.label, on, {
                onEdit { it.copy(receivedIn = if (on) it.receivedIn - field else it.receivedIn + field) }
            })
        }
    }
}
