package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.ChipAvatar
import com.otus.mail.feature.inbox.domain.AccountAddresses
import com.otus.mail.feature.inbox.domain.MailScopeChoice
import com.otus.mail.feature.inbox.domain.domainGroups
import com.otus.mail.feature.inbox.presentation.components.chipAvatar

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
        choice is MailScopeChoice.Address && account != null -> {
            ScopeHeader(choice.address, account.name, listOf(addressAvatar(choice.address)))
        }

        account != null -> {
            ScopeHeader(account.name, addressCount(account.addresses.size), listOf(account.avatar))
        }

        else -> {
            ScopeHeader(ALL_MAIL, "No accounts yet".takeIf { accounts.isEmpty() }, allMailAvatars(accounts))
        }
    }
}

/** "3 addresses" for one account, or "2 accounts · 5 addresses" for several. */
fun allMailSummary(accounts: List<AccountAddresses>): String {
    val addresses = addressCount(accounts.sumOf { it.addresses.size })
    return if (accounts.size > 1) "${accounts.size} accounts · $addresses" else addresses
}

fun addressCount(count: Int): String = if (count == 1) "1 address" else "$count addresses"

/** Each account's initials, or each domain's when there is one account. */
fun allMailAvatars(accounts: List<AccountAddresses>): List<ChipAvatar> =
    if (accounts.size == 1) {
        domainGroups(accounts).map { domainAvatar(it.domain) }
    } else {
        accounts.map { it.avatar }
    }

val AccountAddresses.avatar: ChipAvatar
    get() = chipAvatar(null, name, addresses.firstOrNull().orEmpty())

fun addressAvatar(address: String): ChipAvatar = chipAvatar(null, "", address)

fun domainAvatar(domain: String): ChipAvatar = chipAvatar(null, domain, "")

const val ALL_MAIL = "All mail"
