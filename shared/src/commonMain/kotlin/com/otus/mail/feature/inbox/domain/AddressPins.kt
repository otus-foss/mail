package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.preferences.StringMapStore

/** The addresses the user pinned to the top of "Show mail from". */
data class AddressPins(
    val pins: List<MailScopeChoice.Address> = emptyList(),
) {
    operator fun contains(address: MailScopeChoice.Address): Boolean = pins.any { it.matches(address) }

    /** Pins [address], or unpins it when it is pinned. */
    fun toggled(address: MailScopeChoice.Address): AddressPins =
        if (address in this) AddressPins(pins.filterNot { it.matches(address) }) else AddressPins(pins + address)

    fun withoutAccount(accountId: String): AddressPins = AddressPins(pins.filterNot { it.accountId == accountId })

    private fun MailScopeChoice.Address.matches(other: MailScopeChoice.Address): Boolean =
        accountId == other.accountId && address.equals(other.address, ignoreCase = true)
}

/**
 * App-private storage for [AddressPins]: each account's pinned addresses, one per line, under its
 * login id. Login ids may hold spaces and colons, and addresses never hold a line break.
 */
interface AddressPinStore : StringMapStore

fun AddressPinStore.readPins(): AddressPins =
    AddressPins(
        read().flatMap { (accountId, lines) ->
            lines.lines().filter(String::isNotBlank).map { MailScopeChoice.Address(accountId, it) }
        },
    )

fun AddressPinStore.writePins(pins: AddressPins) =
    write(
        pins.pins
            .groupBy({ it.accountId }, { it.address })
            .mapValues { (_, addresses) -> addresses.joinToString("\n") },
    )

/** Forgets a removed account's pins. */
fun AddressPinStore.forgetAccount(accountId: String) = writePins(readPins().withoutAccount(accountId))
