package com.otus.mail.core.settings.domain

import com.otus.mail.core.messages.domain.MailScopeChoice
import com.otus.mail.core.storage.StoreSpec
import com.otus.mail.core.storage.TextCodec

/** The addresses the user pinned to the top of "Show mail from". */
data class AddressPins(
    val pins: List<MailScopeChoice.Address> = emptyList(),
) {
    operator fun contains(address: MailScopeChoice.Address): Boolean = pins.any { it.matches(address) }

    /** Pins [address], or unpins it when it is pinned. */
    fun toggled(address: MailScopeChoice.Address): AddressPins =
        if (address in this) AddressPins(pins.filterNot { it.matches(address) }) else AddressPins(pins + address)

    /** Each account's pinned addresses, as [AddressPinStore] keeps them. */
    fun byLogin(): Map<String, List<String>> = pins.groupBy({ it.accountId }, { it.address })

    private fun MailScopeChoice.Address.matches(other: MailScopeChoice.Address): Boolean =
        accountId == other.accountId && address.equals(other.address, ignoreCase = true)

    companion object {
        fun of(byLogin: Map<String, List<String>>): AddressPins =
            AddressPins(
                byLogin.flatMap { (accountId, addresses) -> addresses.map { MailScopeChoice.Address(accountId, it) } },
            )
    }
}

/**
 * App-private storage for [AddressPins]: each account's pinned addresses, one per line, under its
 * login id. Login ids may hold spaces and colons, and addresses never hold a line break.
 */
object AddressPinStore : StoreSpec<String, List<String>>("address_pins", TextCodec.Text, TextCodec.Lines)
