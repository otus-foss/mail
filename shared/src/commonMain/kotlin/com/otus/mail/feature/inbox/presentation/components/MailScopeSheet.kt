package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.shared.selection.components.ScopeOption
import com.otus.designsystem.shared.selection.components.ScopeRow
import com.otus.designsystem.shared.selection.components.ScopeSheet
import com.otus.mail.feature.inbox.domain.AccountAddresses
import com.otus.mail.feature.inbox.domain.AddressPins
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.avatarOf
import com.otus.mail.feature.inbox.domain.searchAddresses
import com.otus.mail.feature.inbox.presentation.ALL_MAIL
import com.otus.mail.feature.inbox.presentation.allMailAvatars
import com.otus.mail.feature.inbox.presentation.allMailSummary

/**
 * "Show mail from": every account, one account, or one address. [count] gives each choice's
 * count, or null for none. Choosing a row applies it at once and keeps the sheet open; a long
 * press on an address calls [onTogglePin]. One account lists its addresses by domain, several
 * accounts list theirs under each account.
 */
@Composable
fun MailScopeSheet(
    accounts: List<AccountAddresses>,
    chosen: MailScopeChoice,
    pins: AddressPins,
    count: (MailScopeChoice) -> String?,
    onChoose: (MailScopeChoice) -> Unit,
    onTogglePin: (MailScopeChoice.Address) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The group holding the chosen scope starts open.
    var expanded by rememberSaveable { mutableStateOf(listOfNotNull(groupKey(chosen, accounts))) }
    val total = accounts.sumOf { it.addresses.size }
    val rows =
        MailScopeRows(accounts, chosen, count, onChoose, onTogglePin, expanded) { key, open ->
            expanded = if (open) expanded + key else expanded - key
        }
    ScopeSheet(
        "Show mail from",
        onDismiss,
        search =
            if (total >= SEARCH_FROM) {
                { PickerSearch(query, { query = it }, "Search $total addresses…") }
            } else {
                null
            },
        footnote = "Long-press to pin.",
    ) {
        if (query.isNotBlank()) {
            searchAddresses(accounts, query).forEach { rows.Address(it, it in pins) }
        } else {
            ScopeRow(
                ScopeOption(ALL_MAIL, allMailAvatars(accounts), allMailSummary(accounts), count(MailScopeChoice.All)),
                chosen = chosen == MailScopeChoice.All,
                onClick = { onChoose(MailScopeChoice.All) },
            )
            Pinned(pins, accounts, rows)
            if (accounts.size > 1) rows.Accounts(accounts) else rows.Domains(accounts)
        }
    }
}

/** Pins of addresses the accounts still have, in the order they were pinned. */
@Composable
private fun Pinned(
    pins: AddressPins,
    accounts: List<AccountAddresses>,
    rows: MailScopeRows,
) {
    val known = AddressPins(searchAddresses(accounts, ""))
    val shown = pins.pins.filter { it in known }
    if (shown.isEmpty()) return
    SectionCaption("Pinned")
    shown.forEach { rows.Address(it, pinned = true) }
}

/** The option for one address row, with the avatar the user gave it in [accounts]. */
internal fun addressOption(
    address: MailScopeChoice.Address,
    accounts: List<AccountAddresses>,
    count: String?,
    pinned: Boolean,
): ScopeOption =
    ScopeOption(
        address.address,
        listOf(chipAvatar(accounts.avatarOf(address), "", address.address)),
        count = count,
        pinned = pinned,
    )

private const val SEARCH_FROM = 8
