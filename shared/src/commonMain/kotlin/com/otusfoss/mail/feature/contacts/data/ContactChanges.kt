package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jmap.ChangesMethod
import com.otusfoss.core.jmap.ChangesResult
import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapCall
import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.JmapResponse
import com.otusfoss.core.jmap.JmapSession
import com.otusfoss.core.jmap.JmapType
import com.otusfoss.core.jmap.contacts.AddressBook
import com.otusfoss.core.jmap.contacts.AddressBookType
import com.otusfoss.core.jmap.contacts.ContactCardType
import com.otusfoss.core.jmap.contacts.contactCardPropertiesWithVCard
import com.otusfoss.core.jscontact.Card
import com.otusfoss.mail.core.mail.unlessRefused

/**
 * [account]'s books and cards, brought up to date from [cached]. With both saved states, one
 * request asks `AddressBook/changes` and `ContactCard/changes` (RFC 8620, section 5.2; RFC 9610,
 * sections 2.2 and 3.2) and gets what they name. Without a state, or when the server refuses the
 * changes, such as with `cannotCalculateChanges`, or has more than one page of them, it reads
 * every book and card again.
 */
internal suspend fun JmapConnection.syncAccount(
    session: JmapSession,
    account: ContactAccount,
    cached: AccountContacts?,
): AccountContacts = cached?.let { followChanges(session, account, it) } ?: readAll(session, account)

private suspend fun JmapConnection.followChanges(
    session: JmapSession,
    account: ContactAccount,
    cached: AccountContacts,
): AccountContacts? {
    val bookState = cached.account.bookState
    val cardState = cached.account.cardState
    if (bookState == null || cardState == null) return null
    val max = session.getBatch
    lateinit var books: ChangeCalls<AddressBook>
    lateinit var cards: ChangeCalls<Card>
    val response =
        send {
            books = changeCalls(AddressBookType, account.id, bookState, max, properties = null)
            cards = changeCalls(ContactCardType, account.id, cardState, max, contactCardPropertiesWithVCard)
        }
    return applied(account, cached, books.read(response), cards.read(response))
}

/** [cached] with [books] and [cards] applied; null when the server refused either. */
private suspend fun JmapConnection.applied(
    account: ContactAccount,
    cached: AccountContacts,
    books: Changed<AddressBook>?,
    cards: Changed<Card>?,
): AccountContacts? {
    val bookPart =
        books?.let { changed ->
            changed.fetched?.let { changed.applyTo(cached.books, it) { book -> book.id } }
        }
    // A server that does not know `vCard` refuses the get, so it goes again without properties.
    val cardPart =
        cards?.let { changed ->
            (changed.fetched ?: cardsWithIds(account.id, changed.changes.created + changed.changes.updated))
                ?.let { changed.applyTo(cached.cards, it) { card -> card.id } }
        }
    if (bookPart == null || cardPart == null) return null
    return AccountContacts(
        account.copy(bookState = bookPart.first, cardState = cardPart.first),
        bookPart.second,
        cardPart.second,
    )
}

private suspend fun JmapConnection.cardsWithIds(
    accountId: String,
    ids: List<String>,
): List<Card>? = unlessRefused { call(GetMethod(ContactCardType, accountId, JmapIds.Of(ids))).list }

private suspend fun JmapConnection.readAll(
    session: JmapSession,
    account: ContactAccount,
): AccountContacts {
    val books = call(GetMethod(AddressBookType, account.id))
    val cards = allCards(account.id, session.getBatch)
    return AccountContacts(
        account.copy(bookState = books.state.ifEmpty { null }, cardState = cards.state),
        books.list,
        cards.list,
    )
}

/** A `/changes` call and the gets of what it created and updated, by result reference. */
private class ChangeCalls<T>(
    private val changes: JmapCall<ChangesResult>,
    private val created: JmapCall<GetResult<T>>,
    private val updated: JmapCall<GetResult<T>>,
) {
    /** The changes; null when the server refused them or has more than this page. */
    fun read(response: JmapResponse): Changed<T>? =
        unlessRefused { response[changes] }
            ?.takeUnless { it.hasMoreChanges }
            ?.let { Changed(it, unlessRefused { response[created].list + response[updated].list }) }
}

/** One page of changes and the created and updated objects; [fetched] is null when the server refused a get. */
private class Changed<T>(
    val changes: ChangesResult,
    val fetched: List<T>?,
) {
    /** The new state, and [list] patched with [fetched] objects. */
    fun applyTo(
        list: List<T>,
        fetched: List<T>,
        id: (T) -> String?,
    ): Pair<String, List<T>> = changes.newState to list.patched(changes, fetched, id)
}

private fun <T> JmapRequestBuilder.changeCalls(
    type: JmapType<T>,
    accountId: String,
    state: String,
    max: Int,
    properties: List<String>?,
): ChangeCalls<T> {
    val changes = call(ChangesMethod(type, accountId, state, max))
    return ChangeCalls(
        changes,
        call(GetMethod(type, accountId, JmapIds.Result(changes.resultOf("/created")), properties)),
        call(GetMethod(type, accountId, JmapIds.Result(changes.resultOf("/updated")), properties)),
    )
}

/**
 * The list without what [changes] named, plus [fetched]. A created or updated object the get did
 * not find was destroyed since, so it goes too.
 */
private fun <T> List<T>.patched(
    changes: ChangesResult,
    fetched: List<T>,
    id: (T) -> String?,
): List<T> {
    val gone = (changes.created + changes.updated + changes.destroyed).toSet()
    return filterNot { id(it) in gone } + fetched
}
