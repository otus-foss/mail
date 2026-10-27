package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.people.components.PersonAvatar
import com.otus.designsystem.shared.selection.components.Dropdown
import com.otus.designsystem.shared.selection.components.DropdownRow
import com.otus.designsystem.shared.selection.components.ListCaption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.theme.ComposeTokens
import com.otus.designsystem.theme.MenuTokens
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.presentation.ComposeAccount

/**
 * The addresses a message can be sent from, under the Send-from pill: the [recent] ones first,
 * then the rest by account. A search field filters them once there are [SEARCH_THRESHOLD] or more.
 * Choosing one runs [onSelect] and closes the list.
 */
@Composable
internal fun FromDropdown(
    identities: List<SendIdentity>,
    accounts: List<ComposeAccount>,
    recent: List<String>,
    selected: MailSender?,
    onSelect: (SendIdentity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = identities.filter { it.matches(query) }
    val recentShown =
        recent.mapNotNull { address -> shown.firstOrNull { it.sender.address.equals(address, ignoreCase = true) } }
    val choose = { identity: SendIdentity ->
        onDismiss()
        onSelect(identity)
    }
    Dropdown(onDismiss) {
        if (identities.size >= SEARCH_THRESHOLD) {
            PickerSearch(
                query,
                { query = it },
                "Search ${identities.size} addresses",
                Modifier.padding(MenuTokens.inset),
            )
        }
        IdentitySection("Recent", recentShown, selected, choose)
        accounts.forEach { account ->
            IdentitySection(
                account.name,
                shown.filter { it.accountId == account.id } - recentShown.toSet(),
                selected,
                choose,
            )
        }
    }
}

@Composable
private fun IdentitySection(
    title: String,
    identities: List<SendIdentity>,
    selected: MailSender?,
    onSelect: (SendIdentity) -> Unit,
) {
    if (identities.isEmpty()) return
    ListCaption(title)
    identities.forEach { identity ->
        val sender = identity.sender
        val named = sender.name.isNotBlank() && sender.name != sender.address
        DropdownRow(
            if (named) sender.name else sender.address,
            selected =
                selected != null && sender.accountKey == selected.accountKey &&
                    sender.address.equals(selected.address, ignoreCase = true),
            onClick = { onSelect(identity) },
            detail = sender.address.takeIf { named },
        ) { PersonAvatar(chipAvatar(sender.avatar, sender.name, sender.address), ComposeTokens.dropdownAvatarSize) }
    }
}

private fun SendIdentity.matches(query: String): Boolean =
    query.isBlank() || listOf(sender.address, sender.name).any { it.contains(query.trim(), ignoreCase = true) }

private const val SEARCH_THRESHOLD = 8
