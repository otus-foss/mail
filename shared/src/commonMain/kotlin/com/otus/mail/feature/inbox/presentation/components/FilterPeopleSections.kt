package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.elements.ChipLabel
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_person_add
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.RecipientField
import com.otus.mail.feature.inbox.presentation.FilterOptions

/** Senders to filter by: the chosen ones and the most frequent in the list, and a typed address. */
@Composable
internal fun FromSection(
    draft: InboxFilters,
    senders: List<MailSender>,
    onEdit: FilterEdit,
    onAddSender: () -> Unit,
) {
    FilterSection("From") {
        senders.forEach { sender ->
            val on = sender.address in draft.from
            val spoken = listOf(sender.name, sender.address).filter(String::isNotBlank).joinToString(", ")
            FilterChip(
                ChipLabel(sender.name.ifBlank { sender.address }, spoken = spoken),
                on,
                { onEdit { it.copy(from = if (on) it.from - sender.address else it.from + sender.address) } },
            ) { PersonAvatar(sender.chipAvatar, FilterTokens.chipAvatarSize) }
        }
        FilterChip("Add sender…", selected = false, onClick = onAddSender, role = FilterChipRole.Button) {
            Symbol(Res.drawable.symbol_person_add, size = FilterTokens.chipIconSize)
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
            val on = account.id in draft.accounts
            FilterChip(account.name, on, {
                onEdit { it.copy(accounts = if (on) it.accounts - account.id else it.accounts + account.id) }
            }) { PersonAvatar(account.avatar, FilterTokens.chipAvatarSize) }
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

/** Whether the user's address was in To, Cc, or Bcc. */
@Composable
internal fun ReceivedAsSection(
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    FilterSection("Received as") {
        RecipientField.entries.forEach { field ->
            val on = field in draft.receivedAs
            FilterChip(field.label, on, {
                onEdit { it.copy(receivedAs = if (on) it.receivedAs - field else it.receivedAs + field) }
            })
        }
    }
}
