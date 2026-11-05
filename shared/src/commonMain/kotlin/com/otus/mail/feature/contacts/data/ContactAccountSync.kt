package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.GetResult
import com.otus.jmap.JmapCall
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.contacts.AddressBook
import com.otus.jmap.contacts.AddressBookType
import com.otus.jmap.contacts.ContactCardType
import com.otus.jmap.contacts.contactCardPropertiesWithVCard
import com.otus.jscontact.Card
import com.otus.mail.core.mail.ObjectChangeCalls
import com.otus.mail.core.mail.objectChangeCalls
import com.otus.mail.core.mail.unlessRefused
import kotlinx.serialization.json.JsonPrimitive

/**
 * Brings the account [id]'s books and cards up to date from [cached]. With saved states, one
 * request asks for `AddressBook/changes` and `ContactCard/changes` (RFC 9610, sections 2.2 and
 * 3.2) and the objects they name. A type whose changes the server refuses, such as with
 * `cannotCalculateChanges`, or has more of than one call returns, is read anew: every book, or
 * every card a page at a time. [page] is both the page size and the most card changes asked for,
 * so each `/get` stays within the server's `maxObjectsInGet`.
 */
internal suspend fun JmapConnection.syncAccount(
    id: String,
    account: ContactAccount,
    cached: AccountContacts?,
    page: Int,
): AccountSync {
    val saved = cached?.account
    val vCard = saved?.vCard ?: true
    var bookChanges: ObjectChangeCalls<AddressBook>? = null
    var cardChanges: ObjectChangeCalls<Card>? = null
    lateinit var cardsNow: JmapCall<GetResult<Card>>
    val response =
        send {
            bookChanges = saved?.bookState?.let { objectChangeCalls(AddressBookType, id, it, MAX_BOOK_CHANGES) }
            cardChanges =
                saved?.cardState?.let {
                    objectChangeCalls(ContactCardType, id, it, page, contactCardPropertiesWithVCard.takeIf { vCard })
                }
            cardsNow = call(GetMethod(ContactCardType, id, JmapIds.Of(emptyList()), listOf("id")))
        }
    val cachedBooks = cached?.books.orEmpty()
    val books =
        bookChanges?.read(response)?.let { patched(cachedBooks, it, AddressBook::id) }
            ?: call(GetMethod(AddressBookType, id)).let {
                replaced(cachedBooks, it.list, it.state.ifEmpty { null }, AddressBook::id)
            }
    val cachedCards = cached?.cards.orEmpty()
    val cardPatch = cardChanges?.read(response)
    val fetched = if (cardPatch == null) allCards(id, page, vCard) else null
    val cards =
        cardPatch?.let { patched(cachedCards, it, Card::id) }
            ?: replaced(
                cachedCards,
                fetched?.cards.orEmpty(),
                unlessRefused { response[cardsNow] }?.state?.ifEmpty { null },
                Card::id,
            )
    return AccountSync(id, account.copy(vCard = fetched?.vCard ?: vCard), books, cards)
}

/** Every card of [account] and whether they hold `vCard`, which a server may refuse to send. */
private class FetchedCards(
    val cards: List<Card>,
    val vCard: Boolean,
)

/**
 * Every card of [account], [page] at a time, up to [MAX_CARDS]. Asks for `vCard` too, where an
 * avatar card keeps its style, while [vCard] is set and the server does not refuse it.
 */
private suspend fun JmapConnection.allCards(
    account: String,
    page: Int,
    vCard: Boolean,
): FetchedCards {
    val cards = mutableListOf<Card>()
    var properties: List<String>? = contactCardPropertiesWithVCard.takeIf { vCard }
    while (cards.size < MAX_CARDS) {
        val position = cards.size
        val list =
            try {
                cardPage(account, CardPage(position, page, properties))
            } catch (error: JmapException.MethodError) {
                if (properties == null || error.type != "invalidArguments") throw error
                properties = null
                cardPage(account, CardPage(position, page, null))
            }
        cards += list
        if (list.size < page) break
    }
    return FetchedCards(cards, vCard = properties != null)
}

/** One [page] of [account]'s cards. */
private suspend fun JmapConnection.cardPage(
    account: String,
    page: CardPage,
): List<Card> =
    request {
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

/** Where a page of cards starts, how many it holds, and the properties it asks for; null asks for all. */
private class CardPage(
    val position: Int,
    val size: Int,
    val properties: List<String>?,
)

/** Address books are few; more changes than this cost more to read than the whole list. */
private const val MAX_BOOK_CHANGES = 100

private const val MAX_CARDS = 10_000
