package com.otusfoss.mail.feature.contacts.domain

import kotlinx.serialization.Serializable

/**
 * Where an address book lives: the [loginId] that reaches it, the JMAP [accountId] that holds it,
 * which may differ from the login's mail account, and the server's [bookId].
 */
@Serializable
data class BookKey(
    val loginId: String,
    val accountId: String,
    val bookId: String,
)

/** Where a contact card lives, like [BookKey]; [cardId] is the server's id. */
@Serializable
data class CardKey(
    val loginId: String,
    val accountId: String,
    val cardId: String,
)

/**
 * An address book (RFC 9610, section 2). [canWrite] says whether the user may add, change and
 * remove its cards. A book from someone else's account is [sharedBy] that account's name, else null.
 * [loginName] names the login it came through, for telling two Personal books apart.
 */
data class ContactBook(
    val key: BookKey,
    val name: String,
    val loginName: String,
    val isDefault: Boolean = false,
    val canWrite: Boolean = true,
    val sharedBy: String? = null,
) {
    val isShared: Boolean get() = sharedBy != null
}
