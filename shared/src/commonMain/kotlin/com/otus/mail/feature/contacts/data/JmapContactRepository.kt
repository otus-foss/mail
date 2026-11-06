package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.jmap.contacts.ContactCardFilter
import com.otus.jmap.contacts.ContactCardType
import com.otus.jmap.contacts.cardPatch
import com.otus.jmap.contacts.toCreation
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jscontact.Card
import com.otus.jscontact.Media
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactWriteException
import com.otus.mail.feature.contacts.domain.ContactWriteFailure
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put

/**
 * Address books and cards over JMAP (RFC 9610). Each login may hold contacts in several JMAP
 * accounts: its own, and accounts others share with it (RFC 9670), whose books count as shared.
 */
internal class JmapContactRepository(
    private val connections: JmapConnections,
    private val cache: MailCache,
) : ContactRepository {
    override suspend fun load(): ContactsSnapshot =
        coroutineScope {
            val loaded =
                connections
                    .savedLogins()
                    .map { login -> async { login to runCatching { loadLogin(login) } } }
                    .awaitAll()
            val failed = loaded.filter { (_, result) -> result.isFailure }.map { it.first }
            // A login the server did not answer for shows what the device kept of it.
            val found = loaded.mapNotNull { (_, result) -> result.getOrNull() } + failed.mapNotNull { cachedLogin(it) }
            snapshot(found, failed.map { it.id })
        }

    override suspend fun cached(): ContactsSnapshot? =
        connections
            .savedLogins()
            .mapNotNull { cachedLogin(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { snapshot(it, failedLogins = emptyList()) }

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
        val patch = cardPatch(before, after)
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

    /** Brings [login]'s cached contacts up to date with the server, and shows them. */
    private suspend fun loadLogin(login: JmapLogin): LoginContacts =
        cache.withAccount(login.id) { scoped ->
            val connection = connections.connection(login)
            val session = connection.session()
            if (JmapCapability.CONTACTS !in session.capabilities) {
                return@withAccount LoginContacts(emptyList(), emptyList(), hasContacts = false)
            }
            val own = session.accountFor(JmapCapability.CONTACTS)
            val before = scoped.contacts(login.id)
            val now =
                session.accounts
                    .filterValues { JmapCapability.CONTACTS in it.capabilities }
                    .map { (id, info) ->
                        val account = ContactAccount(id, info.name, id != own && !info.isPersonal, info.isReadOnly)
                        connection.syncAccount(session, account, before.firstOrNull { it.account.id == id })
                    }
            scoped.saveContacts(login.id, before, now)
            loginContacts(login, now)
        }

    private suspend fun cachedLogin(login: JmapLogin): LoginContacts? =
        cache.contacts(login.id).takeIf { it.isNotEmpty() }?.let { loginContacts(login, it) }

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
            connections.savedLogins().firstOrNull { it.id == loginId }
                ?: throw ContactWriteException(ContactWriteFailure.Refused)
        return try {
            block(connections.connection(login))
        } catch (error: JmapException) {
            throw ContactWriteException(error.writeFailure(), error)
        }
    }

    private companion object {
        const val NEW_CARD = "new"
        const val NOT_FOUND = "notFound"
    }
}

/** The cards in [book] with [uid] (RFC 9610, section 3.3). */
private suspend fun JmapConnection.cardsWithUid(
    book: BookKey,
    uid: String,
): List<Card> {
    val filter = ContactCardFilter(inAddressBook = book.bookId, uid = uid).toFilter()
    return request {
        val query = call(QueryMethod(ContactCardType, book.accountId, filter))
        call(GetMethod(ContactCardType, book.accountId, JmapIds.Result(query.resultOf("/ids"))))
    }.list
}

private fun snapshot(
    found: List<LoginContacts>,
    failedLogins: List<String>,
) = ContactsSnapshot(
    books = found.flatMap { it.books },
    cards = found.flatMap { it.cards },
    hasContacts = found.any { it.hasContacts } || failedLogins.isNotEmpty(),
    failedLogins = failedLogins,
)
