package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.JmapSession
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.jmap.contacts.AddressBookOrder
import com.otus.jmap.contacts.AddressBookType
import com.otus.jmap.contacts.ContactCardType
import com.otus.jmap.contacts.contactCardPropertiesWithVCard
import com.otus.jmap.contacts.toCreation
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.jscontact.Card
import com.otus.jscontact.JsContactJson
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Address books and cards over JMAP (RFC 9610). Each login may hold contacts in several JMAP
 * accounts: its own, and accounts others share with it (RFC 9670), whose books count as shared.
 */
internal class JmapContactRepository(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : ContactRepository {
    override suspend fun load(): ContactsSnapshot =
        coroutineScope {
            val all = withContext(Dispatchers.IO) { logins.logins() }
            val loaded = all.map { login -> async { login.id to runCatching { loadLogin(login) } } }.awaitAll()
            val found = loaded.mapNotNull { (_, result) -> result.getOrNull() }
            ContactsSnapshot(
                books = found.flatMap { it.books },
                cards = found.flatMap { it.cards },
                hasContacts = found.any { it.hasContacts } || loaded.any { (_, result) -> result.isFailure },
                failedLogins = loaded.filter { (_, result) -> result.isFailure }.map { it.first },
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

    private suspend fun loadLogin(login: JmapLogin): LoginContacts {
        val connection = connections.connection(login)
        val session = connection.session()
        if (JmapCapability.CONTACTS !in session.capabilities) {
            return LoginContacts(emptyList(), emptyList(), hasContacts = false)
        }
        val own = session.accountFor(JmapCapability.CONTACTS)
        val accounts = session.accounts.filterValues { JmapCapability.CONTACTS in it.capabilities }.keys
        val parts = accounts.map { account -> loadAccount(login, connection, session, account, own) }
        return LoginContacts(parts.flatMap { it.first }, parts.flatMap { it.second }, hasContacts = true)
    }

    private suspend fun loadAccount(
        login: JmapLogin,
        connection: JmapConnection,
        session: JmapSession,
        account: String,
        own: String?,
    ): Pair<List<ContactBook>, List<ContactCard>> {
        val info = session.accounts.getValue(account)
        val shared = account != own && !info.isPersonal
        val availableBooks = connection.call(GetMethod(AddressBookType, account)).list
        val books =
            availableBooks
                .filter { it.myRights.mayRead }
                .sortedWith(AddressBookOrder)
                .map { book ->
                    ContactBook(
                        BookKey(login.id, account, book.id),
                        bookName(book.name, login, info.name),
                        login.name,
                        isDefault = book.isDefault && !shared,
                        canWrite = book.myRights.mayWrite && !info.isReadOnly,
                        sharedBy = info.name.takeIf { shared },
                    )
                }
        val known = books.map { it.key.bookId }.toSet()
        val cards =
            cards(connection, session, account).map { card ->
                ContactCard(
                    CardKey(login.id, account, requireNotNull(card.id)),
                    card,
                    card.addressBookIds
                        .orEmpty()
                        .filter { (id, on) -> on && id in known }
                        .keys
                        .map { BookKey(login.id, account, it) },
                )
            }
        return books to cards.filter { it.books.isNotEmpty() }
    }

    /** Every card of [account], a page at a time, up to [MAX_CARDS]. */
    private suspend fun cards(
        connection: JmapConnection,
        session: JmapSession,
        account: String,
    ): List<Card> {
        val page = (session.maxObjectsInGet ?: DEFAULT_PAGE).toInt().coerceIn(1, DEFAULT_PAGE)
        val cards = mutableListOf<Card>()
        // Asks for `vCard` too, where an avatar card keeps its style, unless the server refuses it.
        var properties: List<String>? = contactCardPropertiesWithVCard
        while (cards.size < MAX_CARDS) {
            val position = cards.size
            val list =
                try {
                    cardPage(connection, account, CardPage(position, page, properties))
                } catch (error: JmapException.MethodError) {
                    if (properties == null || error.type != "invalidArguments") throw error
                    properties = null
                    cardPage(connection, account, CardPage(position, page, null))
                }
            cards += list
            if (list.size < page) break
        }
        return cards
    }

    /** One [page] of [account]'s cards. */
    private suspend fun cardPage(
        connection: JmapConnection,
        account: String,
        page: CardPage,
    ): List<Card> =
        connection
            .request {
                val query =
                    call(
                        QueryMethod(
                            ContactCardType,
                            account,
                            limit = page.size,
                            extra = mapOf("position" to JsonPrimitive(page.position)),
                        ),
                    )
                call(GetMethod(ContactCardType, account, JmapIds.Result(query.resultOf("/ids")), page.properties))
            }.list

    /**
     * Runs a change. A missing connection, a sign-in the server rejects, or a server error is
     * [ContactWriteFailure.Offline], so the change waits and goes again later instead of failing.
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
            throw when (error) {
                is JmapException.MethodError -> refused(error.type, error)
                else -> ContactWriteException(ContactWriteFailure.Offline, error)
            }
        }
    }

    private class LoginContacts(
        val books: List<ContactBook>,
        val cards: List<ContactCard>,
        val hasContacts: Boolean,
    )

    private companion object {
        const val NEW_CARD = "new"
        const val NOT_FOUND = "notFound"
        const val DEFAULT_PAGE = 500
        const val MAX_CARDS = 10_000
    }
}

private fun refused(
    type: String,
    cause: Throwable? = null,
): ContactWriteException =
    ContactWriteException(if (type == "forbidden") ContactWriteFailure.ReadOnly else ContactWriteFailure.Refused, cause)

/**
 * A PatchObject that sets each top-level property that differs between [before] and [after], and
 * removes the ones [after] dropped. Whole properties keep the patch valid on any server, since a
 * path into a property needs its parent to exist there.
 */
internal fun patch(
    before: Card,
    after: Card,
): JsonObject {
    val old = before.json()
    val new = after.json()
    return JsonObject(
        (old.keys + new.keys)
            .filter { it != "id" && old[it] != new[it] }
            .associateWith { new[it] ?: JsonNull },
    )
}

private fun Card.json(): JsonObject = JsContactJson.encodeToJsonElement(Card.serializer(), this).jsonObject

/**
 * A book's name without the account in brackets that some servers append, such as Stalwart's
 * "Address Book (ada@example.org)" or "Address Book (ada.lovelace)" with the JMAP [account]'s
 * name: the row already says which login the book comes from.
 */
internal fun bookName(
    name: String,
    login: JmapLogin,
    account: String,
): String {
    val own = setOf(login.username, login.name, login.username.substringBefore('@'), account).map { it.lowercase() }
    val bracket = Regex("""\s*\(([^()]*)\)\s*$""").find(name)
    val bracketed = bracket?.groupValues?.get(1)
    val inside = bracketed?.trim().orEmpty()
    // An account in brackets: the login's or account's own name, or anything shaped like a
    // username or address, such as "ada.lovelace" or "ada@example.org".
    val isAccount = inside.lowercase() in own || (' ' !in inside && ('@' in inside || '.' in inside))
    val trimmed = if (bracket != null && isAccount) name.removeRange(bracket.range) else name
    return trimmed.trim().ifBlank { "Contacts" }
}

/** Where a page of cards starts, how many it holds, and the properties it asks for; null asks for all. */
private class CardPage(
    val position: Int,
    val size: Int,
    val properties: List<String>?,
)
