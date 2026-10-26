package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card

/**
 * Every login's address books and cards (RFC 9610). Logins whose server has no contacts are left
 * out; a login that failed to load is named in [ContactsSnapshot.failedLogins].
 */
interface ContactRepository {
    suspend fun load(): ContactsSnapshot

    /** Adds [card] to [book] and returns where it was saved. */
    suspend fun create(
        book: BookKey,
        card: Card,
    ): CardKey

    /** Replaces the properties that differ between [before] and [after]; others stay as the server has them. */
    suspend fun update(
        key: CardKey,
        before: Card,
        after: Card,
    )

    suspend fun delete(keys: List<CardKey>)
}

/**
 * What [ContactRepository.load] found. [hasContacts] is false when no login's server offers
 * `urn:ietf:params:jmap:contacts`, which is different from having no cards yet.
 */
data class ContactsSnapshot(
    val books: List<ContactBook>,
    val cards: List<ContactCard>,
    val hasContacts: Boolean,
    val failedLogins: List<String> = emptyList(),
)

/** Why a change did not reach the server. */
enum class ContactWriteFailure {
    /** No connection; the change waits on the device. */
    Offline,

    /** The book no longer lets the user change it. */
    ReadOnly,

    /** The server refused the change for another reason. */
    Refused,
}

class ContactWriteException(
    val failure: ContactWriteFailure,
    cause: Throwable? = null,
) : Exception("The contact change failed: $failure", cause)
