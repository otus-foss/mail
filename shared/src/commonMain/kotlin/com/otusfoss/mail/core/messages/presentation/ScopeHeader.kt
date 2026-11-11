package com.otusfoss.mail.core.messages.presentation

import com.otusfoss.core.designsystem.shared.people.components.ChipAvatar
import com.otusfoss.mail.core.messages.domain.AccountAddresses
import com.otusfoss.mail.core.messages.domain.DomainGroup
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.avatarOf
import com.otusfoss.mail.core.messages.domain.domainGroups
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.core.settings.presentation.title

/** How the sidebar header names the chosen scope. */
data class ScopeHeader(
    val title: String,
    val subtitle: String?,
    val avatars: List<ChipAvatar>,
)

/**
 * All mail shows each account's initials, or each domain's when there is one account. An account
 * shows its name over its address count, and an address shows its account's name below it.
 */
fun scopeHeader(
    choice: MailScopeChoice,
    accounts: List<AccountAddresses>,
): ScopeHeader {
    val account = accounts.firstOrNull { it.accountId == choice.accountId }
    return when {
        choice is MailScopeChoice.Address && account != null -> account.addressHeader(choice.address)
        account != null -> ScopeHeader(account.name, addressCount(account.addresses.size), listOf(account.avatar))
        else -> ScopeHeader(ALL_MAIL, "No accounts yet".takeIf { accounts.isEmpty() }, allMailAvatars(accounts))
    }
}

private fun AccountAddresses.addressHeader(address: String): ScopeHeader =
    ScopeHeader(address, name, listOf(avatar(address)))

/** "3 addresses" for one account, or "2 accounts · 5 addresses" for several. */
fun allMailSummary(accounts: List<AccountAddresses>): String {
    val addresses = addressCount(accounts.sumOf { it.addresses.size })
    return if (accounts.size > 1) "${accounts.size} accounts · $addresses" else addresses
}

fun addressCount(count: Int): String = if (count == 1) "1 address" else "$count addresses"

/** Each account's avatar, or each domain's when there is one account. */
fun allMailAvatars(accounts: List<AccountAddresses>): List<ChipAvatar> =
    if (accounts.size == 1) {
        domainGroups(accounts).map { domainAvatar(it, accounts) }
    } else {
        accounts.map { it.avatar }
    }

/** The avatar of the account's first address, which giving all its addresses one avatar sets, else its initials. */
val AccountAddresses.avatar: ChipAvatar
    get() = addresses.firstOrNull().orEmpty().let { first -> chipAvatar(avatarOf(first), name, first) }

/** The avatar the user gave [address], else initials from the address. */
fun AccountAddresses.avatar(address: String): ChipAvatar = chipAvatar(avatarOf(address), "", address)

/** The first avatar the user gave an address of [group], else the domain's initials. */
fun domainAvatar(
    group: DomainGroup,
    accounts: List<AccountAddresses>,
): ChipAvatar = chipAvatar(group.addresses.firstNotNullOfOrNull(accounts::avatarOf), group.domain, "")

const val ALL_MAIL = "All mail"
