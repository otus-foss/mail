package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.Media

/**
 * Every login's address books and cards (RFC 9610). Logins whose server has no contacts are left
 * out; a login that failed to load is named in [ContactsSnapshot.failedLogins].
 */
interface ContactRepository {
    /**
     * Brings the contacts up to date with every server and returns them. A login whose server does
     * not answer is named in [ContactsSnapshot.failedLogins] and shows what the device kept of it.
     */
    suspend fun load(): ContactsSnapshot

    /** What the device kept from the last load, without asking any server; null when it kept nothing. */
    suspend fun cached(): ContactsSnapshot? = null

    /** Adds [card] to [book] and returns where it was saved. */
    suspend fun create(
        book: BookKey,
        card: Card,
    ): CardKey

    /**
     * The card in [book] with [card]'s uid, other than those in [ignore], that a create of [card]
     * whose answer was lost may have stored; null when there is none, so the create goes again.
     */
    suspend fun findCreated(
        book: BookKey,
        card: Card,
        ignore: Set<CardKey>,
    ): CardKey? = null

    /** Replaces the properties that differ between [before] and [after]; others stay as the server has them. */
    suspend fun update(
        key: CardKey,
        before: Card,
        after: Card,
    )

    suspend fun delete(keys: List<CardKey>)

    /** The image of [photo], a photo of the card [key] that the server keeps as a blob; null when it cannot be read. */
    suspend fun photo(
        key: CardKey,
        photo: Media,
    ): ByteArray? = null
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

    /**
     * The server's answer did not say whether it took the change, such as an internal error or an
     * answer the app cannot read. The change goes again a few times, then it is set aside like a
     * refusal, so it does not hold up the changes after it.
     */
    Failing,
}

class ContactWriteException(
    val failure: ContactWriteFailure,
    cause: Throwable? = null,
) : Exception("The contact change failed: $failure", cause)
