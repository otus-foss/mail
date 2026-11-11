package com.otusfoss.mail.core.cache

/** The kinds of server objects Mail keeps on the device. Each login keeps its own. */
enum class CacheKind {
    /** Mailbox objects (RFC 8621, section 2), with their counts. */
    Mailbox,

    /** Email objects with every property a list row shows. */
    Email,

    /** Email objects with only what a conversation counts: people, date, keywords, and mailboxes. */
    Member,

    /** Thread objects (RFC 8621, section 3): the ids of a thread's emails. */
    Thread,

    /** An email's text and HTML body values. Emails never change, so a body stays valid. */
    Body,

    /** The Inbox category the device sorted an email into, with its reasons and the classifier's version. */
    Category,

    /** AddressBook objects (RFC 9610, section 2), by account id and book id. */
    AddressBook,

    /** ContactCard objects (RFC 9610, section 3), by account id and card id. */
    ContactCard,
}

/**
 * What Mail keeps of each login's mail on the device, so lists open before the server answers and
 * stay readable offline. It stores server objects as JSON text under their id, and named values
 * such as sync states. It knows nothing of JMAP; callers encode and decode the objects.
 */
interface MailCache {
    /** The objects of [kind] with [ids] that the cache holds, by id. */
    suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String>

    /** Every object of [kind] the cache holds for [loginId], by id. */
    suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String>

    /** The value saved under [key], such as an `Email` state; null when none is. */
    suspend fun value(
        loginId: String,
        key: String,
    ): String?

    /** Applies [changes] together: either all of them are saved or none is. */
    suspend fun write(
        loginId: String,
        changes: CacheWrite.() -> Unit,
    )

    /** Removes [kind]'s objects received before [epochMillis], of every login. */
    suspend fun trim(
        kind: CacheKind,
        epochMillis: Long,
    )

    /** Removes everything kept for [loginId], such as when the account is removed. */
    suspend fun forget(loginId: String)
}

/** The changes of one [MailCache.write]. */
interface CacheWrite {
    /** Saves [json] as the object of [kind] with [id], replacing one saved before. */
    fun put(
        kind: CacheKind,
        id: String,
        json: String,
        receivedMillis: Long? = null,
    )

    fun remove(
        kind: CacheKind,
        ids: Collection<String>,
    )

    /** Removes every object of [kind]. */
    fun clear(kind: CacheKind)

    /** Saves [value] under [key]; null removes it. */
    fun putValue(
        key: String,
        value: String?,
    )
}
