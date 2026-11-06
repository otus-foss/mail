package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mail.EmailAddressKey

/**
 * The user's own addresses per login: its JMAP identities plus the sign-in address. Addresses
 * compare without case, because mail servers treat them that way.
 */
class OwnAddresses(
    byLogin: Map<String, Collection<String>>,
) {
    private val addresses = byLogin.mapValues { (_, list) -> list.map { EmailAddressKey(it) }.toSet() }

    fun isMine(
        loginId: String,
        address: String,
    ): Boolean = EmailAddressKey(address) in addresses[loginId].orEmpty()

    /** Whether [address] is the user's own on any login, such as mail they sent themselves. */
    fun isMineAnywhere(address: String): Boolean = addresses.values.any { EmailAddressKey(address) in it }
}
