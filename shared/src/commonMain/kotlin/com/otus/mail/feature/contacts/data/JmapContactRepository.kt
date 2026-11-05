package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.jmap.contacts.AddressBookOrder
import com.otus.jmap.contacts.ContactCardType
import com.otus.jmap.contacts.toCreation
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jscontact.Card
import com.otus.jscontact.Media
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Address books and cards over JMAP (RFC 9610). Each login may hold contacts in several JMAP
 * accounts: its own, and accounts others share with it (RFC 9670), whose books count as shared.
 * [cache] keeps them on the device, so they show offline and each load fetches only what changed.
 */
internal class JmapContactRepository(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache,
) : ContactRepository {
    override suspend fun load(): ContactsSnapshot =
        coroutineScope {
            val loaded =
                withContext(Dispatchers.IO) { logins.logins() }
                    .map { login ->
                        async {
                            runCatching { loadLogin(login) }
                                .getOrElse { LoginContacts(login, emptyList(), hasContacts = false, failed = true) }
                        }
                    }.awaitAll()
            ContactsSnapshot(
                books = loaded.flatMap { it.books },
                cards = loaded.flatMap { it.cards },
                hasContacts = loaded.any { it.hasContacts || it.failed },
                failedLogins = loaded.filter { it.failed }.map { it.loginId },
            )
        }

    override suspend fun create(
        book: BookKey,
        card: Card,
    ): CardKey {
        val result =
            write(book.loginId) { connection ->
                connection.call(
                    SetMethod(
                        ContactCardType,
                        book.accountId,
                        create = mapOf(NEW_CARD to card.copy(addressBookIds = mapOf(book.bookId to true)).toCreation()),
                    ),
                )
            }
        val error = result.firstError
        if (error != null) throw refused(error.type)
        val id = result.createdId(NEW_CARD) ?: throw ContactWriteException(ContactWriteFailure.Refused)
        return CardKey(book.loginId, book.accountId, id)
    }

    override suspend fun findCreated(
        book: BookKey,
        card: Card,
        ignore: Set<CardKey>,
    ): CardKey? {
        val uid = card.uid ?: return null
        val found =
            try {
                write(book.loginId) { it.cardsWithUid(book, uid) }
            } catch (refusal: ContactWriteException) {
                // A server that cannot look cards up by uid gets the create again, as it would have.
                // Without an answer the create waits instead, since the card may be there.
                if (refusal.failure in setOf(ContactWriteFailure.Offline, ContactWriteFailure.Failing)) throw refusal
                emptyList()
            }
        return found
            .mapNotNull { it.id }
            .map { CardKey(book.loginId, book.accountId, it) }
            .firstOrNull { it !in ignore }
    }

    override suspend fun update(
        key: CardKey,
        before: Card,
        after: Card,
    ) {
        val patch = patch(before, after)
        if (patch.isEmpty()) return
        val result =
            write(key.loginId) { connection ->
                connection.call(SetMethod(ContactCardType, key.accountId, update = mapOf(key.cardId to patch)))
            }
        val error = result.firstError
        if (error != null) throw refused(error.type)
    }

    override suspend fun delete(keys: List<CardKey>) {
        keys.groupBy { it.loginId to it.accountId }.forEach { (place, cards) ->
            val result =
                write(place.first) { connection ->
                    connection.call(SetMethod(ContactCardType, place.second, destroy = cards.map { it.cardId }))
                }
            // A card someone else already removed is gone either way.
            val error = result.notDestroyed.values.firstOrNull { it.type != NOT_FOUND }
            if (error != null) throw refused(error.type)
        }
    }

    override suspend fun photo(
        key: CardKey,
        photo: Media,
    ): ByteArray? =
        try {
            write(key.loginId) { it.photo(key.accountId, photo) }
        } catch (_: ContactWriteException) {
            null
        }

    /**
     * [login]'s contacts, synced with the server through the cache. When the server cannot be
     * reached, the cached ones show and the login counts as failed.
     */
    private suspend fun loadLogin(login: JmapLogin): LoginContacts =
        cache.withAccount(login.id) { cache ->
            val cached = cache.cachedContacts(login.id)
            val synced = runCatching { syncLogin(login, cache, cached) }
            // A removed or replaced login stops here instead of showing what it had cached.
            synced.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            synced.fold(
                onSuccess = { LoginContacts(login, it.orEmpty(), hasContacts = it != null) },
                onFailure = {
                    LoginContacts(
                        login,
                        cached.values.toList(),
                        hasContacts = cached.isNotEmpty(),
                        failed = true,
                    )
                },
            )
        }

    /**
     * Every account of [login] with contacts, brought up to date; null when its server offers no
     * contacts, whose cached ones are then forgotten.
     */
    private suspend fun syncLogin(
        login: JmapLogin,
        cache: MailCache,
        cached: Map<String, AccountContacts>,
    ): List<AccountContacts>? {
        val connection = connections.connection(login)
        val session = connection.session()
        if (JmapCapability.CONTACTS !in session.capabilities) {
            cache.saveContacts(login.id, emptyList(), gone = cached.values.toList())
            return null
        }
        val own = session.accountFor(JmapCapability.CONTACTS)
        val accounts = session.accounts.filterValues { JmapCapability.CONTACTS in it.capabilities }
        val page = (session.maxObjectsInGet ?: DEFAULT_PAGE).toInt().coerceIn(1, DEFAULT_PAGE)
        val synced =
            accounts.map { (id, info) ->
                val account =
                    ContactAccount(info.name, shared = id != own && !info.isPersonal, readOnly = info.isReadOnly)
                connection.syncAccount(id, account, cached[id], page)
            }
        cache.saveContacts(login.id, synced, gone = cached.filterKeys { it !in accounts }.values.toList())
        return synced.map { it.contacts }
    }

    /**
     * Runs a change. A missing connection or a sign-in the server rejects is
     * [ContactWriteFailure.Offline], so the change waits and goes again later; [writeFailure] tells
     * those apart from a refusal of this change alone.
     */
    private suspend fun <T> write(
        loginId: String,
        block: suspend (JmapConnection) -> T,
    ): T {
        val login =
            withContext(Dispatchers.IO) { logins.logins().firstOrNull { it.id == loginId } }
                ?: throw ContactWriteException(ContactWriteFailure.Refused)
        return try {
            block(connections.connection(login))
        } catch (error: JmapException) {
            throw ContactWriteException(error.writeFailure(), error)
        }
    }

    /** What [loadLogin] found for [login], as the screen shows it. */
    private class LoginContacts(
        login: JmapLogin,
        accounts: List<AccountContacts>,
        val hasContacts: Boolean,
        val failed: Boolean = false,
    ) {
        val loginId = login.id
        private val parts = accounts.map { it.shown(login) }
        val books = parts.flatMap { it.first }
        val cards = parts.flatMap { it.second }
    }

    private companion object {
        const val NEW_CARD = "new"
        const val NOT_FOUND = "notFound"
        const val DEFAULT_PAGE = 500
    }
}

/**
 * The books [login] may read in this account, and their cards; cards in no such book are left out.
 * The account's own default book is the default only when the account is not shared.
 */
private fun AccountContacts.shown(login: JmapLogin): Pair<List<ContactBook>, List<ContactCard>> {
    val books =
        books
            .filter { it.myRights.mayRead }
            .sortedWith(AddressBookOrder)
            .map { book ->
                ContactBook(
                    BookKey(login.id, id, book.id),
                    bookName(book.name, login, account.name),
                    login.name,
                    isDefault = book.isDefault && !account.shared,
                    canWrite = book.myRights.mayWrite && !account.readOnly,
                    sharedBy = account.name.takeIf { account.shared },
                )
            }
    val known = books.map { it.key.bookId }.toSet()
    val shown =
        cards.mapNotNull { card ->
            val cardId = card.id ?: return@mapNotNull null
            val inBooks =
                card.addressBookIds
                    .orEmpty()
                    .filter { (book, on) -> on && book in known }
                    .keys
                    .map { BookKey(login.id, id, it) }
            ContactCard(CardKey(login.id, id, cardId), card, inBooks).takeIf { inBooks.isNotEmpty() }
        }
    return books to shown
}

/** The cards in [book] with [uid] (RFC 9610, section 3.3). */
private suspend fun JmapConnection.cardsWithUid(
    book: BookKey,
    uid: String,
): List<Card> {
    val filter =
        buildJsonObject {
            put("inAddressBook", book.bookId)
            put("uid", uid)
        }
    return request {
        val query = call(QueryMethod(ContactCardType, book.accountId, filter))
        call(GetMethod(ContactCardType, book.accountId, JmapIds.Result(query.resultOf("/ids"))))
    }.list
}
