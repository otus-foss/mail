package com.otus.mail.feature.inbox.domain

import com.otus.avatar.ResolvedAvatar

/** The addresses one account receives mail at, as "Show mail from" lists them. */
data class AccountAddresses(
    val accountId: String,
    val name: String,
    val addresses: List<String>,
    /** The avatar the user gave each address, by lowercase address; an address without one is missing. */
    val avatars: Map<String, ResolvedAvatar> = emptyMap(),
) {
    /** The avatar the user gave [address], if any. */
    fun avatarOf(address: String): ResolvedAvatar? = avatars[address.lowercase()]

    companion object {
        /**
         * The sign-in [username] when it is an address, then the account's JMAP [identities], each
         * once regardless of case. Wildcard identities, such as `*@example.org`, name no one
         * address, so they are left out.
         */
        fun of(
            accountId: String,
            name: String,
            username: String,
            identities: List<String>,
        ): AccountAddresses =
            AccountAddresses(
                accountId,
                name,
                (listOf(username) + identities)
                    .map(String::trim)
                    .filter { '@' in it && '*' !in it }
                    .distinctBy(String::lowercase),
            )
    }
}

/** Addresses that share the part after the `@`, in the order they were listed. */
data class DomainGroup(
    val domain: String,
    val addresses: List<MailScopeChoice.Address>,
)

/** Every address of [accounts], grouped by domain in order of first appearance. */
fun domainGroups(accounts: List<AccountAddresses>): List<DomainGroup> =
    accounts
        .flatMap { account -> account.addresses.map { MailScopeChoice.Address(account.accountId, it) } }
        .groupBy { it.address.substringAfterLast('@').lowercase() }
        .map { (domain, addresses) -> DomainGroup(domain, addresses) }

/** Every address of [accounts] that contains [query], ignoring case, in account order. */
fun searchAddresses(
    accounts: List<AccountAddresses>,
    query: String,
): List<MailScopeChoice.Address> =
    accounts.flatMap { account ->
        account.addresses
            .filter { it.contains(query.trim(), ignoreCase = true) }
            .map { MailScopeChoice.Address(account.accountId, it) }
    }

/**
 * How many inbox [messages] [address] received, by the sidebar's [count] setting: unread ones,
 * all of them, or null while counts are off. Zero reads as null, like the sidebar's counts.
 */
fun addressCount(
    messages: List<MailMessage>,
    address: MailScopeChoice.Address,
    count: SidebarCount,
): Long? {
    if (count == SidebarCount.None) return null
    return messages
        .filter { it.location.mailbox == INBOX_MAILBOX && address.shows(it) }
        .count { count == SidebarCount.Total || it.attributes.unread }
        .toLong()
        .takeIf { it > 0 }
}

/** The avatar the user gave [address] in its account, if any. */
fun List<AccountAddresses>.avatarOf(address: MailScopeChoice.Address): ResolvedAvatar? =
    firstOrNull { it.accountId == address.accountId }?.avatarOf(address.address)
