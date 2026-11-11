package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.GetResult
import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.contacts.ContactCardType
import com.otusfoss.core.jmap.contacts.contactCardPropertiesWithVCard
import com.otusfoss.core.jscontact.Card
import kotlinx.serialization.json.JsonPrimitive

/** Cards and the `ContactCard` state they match; null when the server gave none. */
internal class CardList(
    val list: List<Card>,
    val state: String?,
)

/**
 * Every card of [account], [page] cards at a time, up to [MAX_CARDS]. The state is the first
 * page's, so a card that changes while later pages load shows up in the next changes.
 */
internal suspend fun JmapConnection.allCards(
    account: String,
    page: Int,
): CardList {
    val cards = mutableListOf<Card>()
    var state: String? = null
    // Asks for `vCard` too, where an avatar card keeps its style, unless the server refuses it.
    var properties: List<String>? = contactCardPropertiesWithVCard
    while (cards.size < MAX_CARDS) {
        val position = cards.size
        val result =
            try {
                cardPage(account, CardPage(position, page, properties))
            } catch (error: JmapException.MethodError) {
                if (properties == null || error.type != "invalidArguments") throw error
                properties = null
                cardPage(account, CardPage(position, page, null))
            }
        if (state == null) state = result.state.ifEmpty { null }
        cards += result.list
        if (result.list.size < page) break
    }
    return CardList(cards, state)
}

/** One [page] of [account]'s cards. */
private suspend fun JmapConnection.cardPage(
    account: String,
    page: CardPage,
): GetResult<Card> =
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
    }

/** Where a page of cards starts, how many it holds, and the properties it asks for; null asks for all. */
private class CardPage(
    val position: Int,
    val size: Int,
    val properties: List<String>?,
)

private const val MAX_CARDS = 10_000
