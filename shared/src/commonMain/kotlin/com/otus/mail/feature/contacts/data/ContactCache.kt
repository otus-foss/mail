package com.otus.mail.feature.contacts.data

import com.otus.jmap.JmapJson
import com.otus.jmap.contacts.AddressBook
import com.otus.jscontact.Card
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.decoded
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/**
 * One JMAP account that holds contacts, as the session named it, and the `AddressBook` and
 * `ContactCard` states its cached books and cards match; a null state means they must be read again.
 */
@Serializable
internal data class ContactAccount(
    val id: String,
    val name: String,
    val isShared: Boolean,
    val isReadOnly: Boolean,
    val bookState: String? = null,
    val cardState: String? = null,
)

/** An account's books and cards as the server last sent them, readable or not. */
internal class AccountContacts(
    val account: ContactAccount,
    val books: List<AddressBook>,
    val cards: List<Card>,
)

/**
 * The login's cached contacts, one entry per account; empty before the first load and when the
 * cache cannot be read. A book or card that no longer decodes is left out, and its account starts
 * over, so the next load reads it again.
 */
internal suspend fun MailCache.contacts(loginId: String): List<AccountContacts> =
    try {
        readContacts(loginId)
    } catch (expected: CacheStorageException) {
        emptyList()
    }

/**
 * Makes the cache hold exactly [now], writing only the books and cards that differ from [before],
 * so a load that changed one card rewrites one row. A cache that cannot save them keeps the old
 * copy; the contacts still show, and the next load tries again.
 */
internal suspend fun MailCache.saveContacts(
    loginId: String,
    before: List<AccountContacts>,
    now: List<AccountContacts>,
) {
    try {
        write(loginId) { saveContacts(before, now) }
    } catch (expected: CacheStorageException) {
        // The contacts came from the server, so they show without the copy.
    }
}

private suspend fun MailCache.readContacts(loginId: String): List<AccountContacts> {
    val accounts = value(loginId, CONTACT_ACCOUNTS)?.let(::decodeAccounts).orEmpty()
    if (accounts.isEmpty()) return emptyList()
    val savedBooks = readAll(loginId, CacheKind.AddressBook)
    val savedCards = readAll(loginId, CacheKind.ContactCard)
    val books = savedBooks.decoded(AddressBook.serializer())
    val cards = savedCards.decoded(Card.serializer())
    val booksOk = books.size == savedBooks.size
    val cardsOk = cards.size == savedCards.size
    return accounts.map { account ->
        AccountContacts(
            account.copy(
                bookState = account.bookState.takeIf { booksOk },
                cardState = account.cardState.takeIf { cardsOk },
            ),
            books.inAccount(account.id),
            cards.inAccount(account.id),
        )
    }
}

private fun CacheWrite.saveContacts(
    before: List<AccountContacts>,
    now: List<AccountContacts>,
) {
    replace(CacheKind.AddressBook, before.books(), now.books(), AddressBook.serializer())
    replace(CacheKind.ContactCard, before.cards(), now.cards(), Card.serializer())
    putValue(CONTACT_ACCOUNTS, JmapJson.encodeToString(AccountList, now.map { it.account }))
}

private fun <T> CacheWrite.replace(
    kind: CacheKind,
    before: Map<String, T>,
    now: Map<String, T>,
    serializer: KSerializer<T>,
) {
    remove(kind, before.keys - now.keys)
    now
        .filter { (id, value) -> before[id] != value }
        .forEach { (id, value) -> put(kind, id, JmapJson.encodeToString(serializer, value)) }
}

private fun List<AccountContacts>.books(): Map<String, AddressBook> =
    flatMap { contacts -> contacts.books.map { cacheId(contacts.account.id, it.id) to it } }.toMap()

private fun List<AccountContacts>.cards(): Map<String, Card> =
    flatMap { contacts -> contacts.cards.map { cacheId(contacts.account.id, requireNotNull(it.id)) to it } }.toMap()

private fun <T> Map<String, T>.inAccount(accountId: String): List<T> =
    filterKeys { it.substringBefore(SEPARATOR) == accountId }.values.toList()

/** JMAP ids use only letters, digits, `-`, and `_` (RFC 8620, section 1.2), so a space parts them. */
private fun cacheId(
    accountId: String,
    id: String,
) = "$accountId$SEPARATOR$id"

private fun decodeAccounts(json: String): List<ContactAccount>? =
    try {
        JmapJson.decodeFromString(AccountList, json)
    } catch (expected: SerializationException) {
        null
    } catch (expected: IllegalArgumentException) {
        null
    }

private const val SEPARATOR = ' '

private val AccountList = ListSerializer(ContactAccount.serializer())

/** The saved-value key of the login's [ContactAccount]s. */
private const val CONTACT_ACCOUNTS = "contacts:accounts"
