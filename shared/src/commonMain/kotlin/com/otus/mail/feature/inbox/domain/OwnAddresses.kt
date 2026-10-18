package com.otus.mail.feature.inbox.domain

/**
 * The user's own addresses per login: its JMAP identities plus the sign-in address. Addresses
 * compare without case, because mail servers treat them that way.
 */
class OwnAddresses(
    byLogin: Map<String, Collection<String>>,
) {
    private val addresses = byLogin.mapValues { (_, list) -> list.map { it.trim().lowercase() }.toSet() }

    fun isMine(
        loginId: String,
        address: String,
    ): Boolean = address.trim().lowercase() in addresses[loginId].orEmpty()
}
