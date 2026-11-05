package com.otus.mail.feature.contacts.data

import com.otus.jmap.contacts.AddressBook
import com.otus.jscontact.Card
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.ObjectPatch
import com.otus.mail.core.mail.allObjects
import com.otus.mail.core.mail.putObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * A JMAP account that holds contacts, as the session described it, and the `AddressBook` and
 * `ContactCard` states its cached objects match. [vCard] says whether its cards are fetched with
 * the `vCard` property; a server that refuses the property gets every card property instead.
 */
@Serializable
internal data class ContactAccount(
    val name: String,
    val shared: Boolean,
    val readOnly: Boolean,
    val vCard: Boolean = true,
    val bookState: String? = null,
    val cardState: String? = null,
)

/** One account's address books and cards, as the server or the device has them. */
internal data class AccountContacts(
    val id: String,
    val account: ContactAccount,
    val books: List<AddressBook>,
    val cards: List<Card>,
)

/**
 * The objects of one type after a sync, the state they match, and how the cache changes to match:
 * [saved] are written again and [removed] ids are dropped, so a sync that changed little writes little.
 */
internal class Synced<T>(
    val all: List<T>,
    val state: String?,
    val saved: List<T> = all,
    val removed: Set<String> = emptySet(),
)

/** [cached] with [patch]'s objects in place of those of the same id and without the destroyed ones. */
internal fun <T> patched(
    cached: List<T>,
    patch: ObjectPatch<T>,
    id: (T) -> String?,
): Synced<T> {
    val gone = patch.destroyed + patch.fetched.mapNotNull(id)
    return Synced(cached.filterNot { id(it) in gone } + patch.fetched, patch.state, patch.fetched, patch.destroyed)
}

/** [now], which matches [state], in place of [cached], which is dropped where [now] no longer has it. */
internal fun <T> replaced(
    cached: List<T>,
    now: List<T>,
    state: String?,
    id: (T) -> String?,
): Synced<T> = Synced(now, state, removed = cached.mapNotNull(id).toSet() - now.mapNotNull(id).toSet())

/**
 * Every account's cached contacts for [loginId], by account id; empty when the cache cannot be
 * read, so a sync starts over. Books and cards are saved under `<account>/<id>`, since one login
 * may reach several accounts whose ids are unique only within each one.
 */
internal suspend fun MailCache.cachedContacts(loginId: String): Map<String, AccountContacts> =
    try {
        val books = allObjects(loginId, CacheKind.AddressBook, AddressBook.serializer()).byAccount()
        val cards = allObjects(loginId, CacheKind.ContactCard, Card.serializer()).byAccount()
        allObjects(loginId, CacheKind.ContactAccount, ContactAccount.serializer())
            .mapValues { (id, account) ->
                AccountContacts(id, account, books[id].orEmpty(), cards[id].orEmpty())
            }
    } catch (expected: CacheStorageException) {
        emptyMap()
    }

/**
 * Saves what a sync found for [accounts] and forgets the cached accounts in [gone]. A cache that
 * cannot be written keeps the contacts on screen; the next sync starts over from what is saved.
 */
internal suspend fun MailCache.saveContacts(
    loginId: String,
    accounts: List<AccountSync>,
    gone: List<AccountContacts>,
) {
    try {
        write(loginId) {
            accounts.forEach { saveAccount(it) }
            gone.forEach { forgetAccount(it) }
        }
    } catch (expected: CacheStorageException) {
        // The contacts still show; the next sync starts from what the cache kept.
    }
}

/** What one account's sync found, as [AccountContacts] and as cache changes. */
internal class AccountSync(
    val id: String,
    val account: ContactAccount,
    val books: Synced<AddressBook>,
    val cards: Synced<Card>,
) {
    val contacts: AccountContacts
        get() =
            AccountContacts(
                id,
                account.copy(bookState = books.state, cardState = cards.state),
                books.all,
                cards.all,
            )
}

private fun CacheWrite.saveAccount(sync: AccountSync) {
    putObject(CacheKind.ContactAccount, sync.id, ContactAccount.serializer(), sync.contacts.account)
    saveObjects(CacheKind.AddressBook, sync.id, sync.books, AddressBook.serializer()) { it.id }
    saveObjects(CacheKind.ContactCard, sync.id, sync.cards, Card.serializer()) { it.id }
}

private fun CacheWrite.forgetAccount(contacts: AccountContacts) {
    remove(CacheKind.ContactAccount, listOf(contacts.id))
    remove(CacheKind.AddressBook, contacts.books.map { key(contacts.id, it.id) })
    remove(CacheKind.ContactCard, contacts.cards.mapNotNull { it.id }.map { key(contacts.id, it) })
}

private fun <T> CacheWrite.saveObjects(
    kind: CacheKind,
    account: String,
    synced: Synced<T>,
    serializer: KSerializer<T>,
    id: (T) -> String?,
) {
    remove(kind, synced.removed.map { key(account, it) })
    synced.saved.forEach { value -> id(value)?.let { putObject(kind, key(account, it), serializer, value) } }
}

private fun key(
    account: String,
    id: String,
) = "$account/$id"

private fun <T> Map<String, T>.byAccount(): Map<String, List<T>> =
    entries.groupBy({ it.key.substringBefore('/') }, { it.value })
