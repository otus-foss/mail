package com.otus.mail.core.messages.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.overlays.components.ListSheet
import com.otus.designsystem.shared.rows.elements.SectionCaption
import com.otus.designsystem.shared.selection.components.PickerSearch
import com.otus.designsystem.shared.selection.components.ScopeOption
import com.otus.designsystem.shared.selection.components.ScopeRow
import com.otus.mail.core.messages.domain.AccountAddresses
import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.messages.domain.avatarOf
import com.otus.mail.core.messages.domain.searchAddresses
import com.otus.mail.core.messages.presentation.ALL_MAIL
import com.otus.mail.core.messages.presentation.allMailAvatars
import com.otus.mail.core.messages.presentation.allMailSummary
import com.otus.mail.core.settings.domain.AddressPins

/** "Show mail from" in a sheet, which stays open after a choice and searches many addresses. */
@Composable
fun MailScopeSheet(
    switch: MailScopeSwitch,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val total = switch.accounts.sumOf { it.addresses.size }
    ListSheet(
        "Show mail from",
        onDismiss,
        search =
            if (total >= SEARCH_FROM) {
                { PickerSearch(query, { query = it }, "Search $total addresses…") }
            } else {
                null
            },
        footnote = "Long-press to pin.",
    ) { MailScopeChoices(switch, query) }
}

/**
 * The choices themselves: All mail, the pinned addresses, then each account with its addresses, or
 * the one account's addresses by domain. A [query] lists the matching addresses instead. The group
 * holding the chosen scope starts open.
 */
@Composable
fun MailScopeChoices(
    switch: MailScopeSwitch,
    query: String = "",
) {
    val accounts = switch.accounts
    val chosen = switch.chosen
    var expanded by rememberSaveable { mutableStateOf(listOfNotNull(groupKey(chosen, accounts))) }
    val rows =
        MailScopeRows(accounts, chosen, switch.count, switch.onChoose, switch.onTogglePin, expanded) { key, open ->
            expanded = if (open) expanded + key else expanded - key
        }
    if (query.isNotBlank()) {
        searchAddresses(accounts, query).forEach { rows.Address(it, it in switch.pins) }
    } else {
        ScopeRow(
            ScopeOption(
                ALL_MAIL,
                allMailAvatars(accounts),
                allMailSummary(accounts),
                switch.count(MailScopeChoice.All),
            ),
            chosen = chosen == MailScopeChoice.All,
            onClick = { switch.onChoose(MailScopeChoice.All) },
        )
        Pinned(switch.pins, accounts, rows)
        if (accounts.size > 1) rows.Accounts(accounts) else rows.Domains(accounts)
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
