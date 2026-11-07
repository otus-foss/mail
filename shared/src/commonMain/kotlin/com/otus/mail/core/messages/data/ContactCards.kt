package com.otus.mail.core.messages.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.contacts.ContactCardType
import com.otus.jmap.jmapDecoding
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.messages.domain.MailSender

/**
 * The names and addresses on the first [limit] contact cards of the login's address books
 * (RFC 9610, section 3). Empty when the server has no contacts or cannot list them, so callers
 * still work from mail alone.
 */
internal suspend fun MailAccount.contactCards(limit: Int): List<MailSender> {
    val books = session.accountFor(JmapCapability.CONTACTS) ?: return emptyList()
    return try {
        val cards =
            connection.request {
                val query = call(QueryMethod(ContactCardType, books, limit = limit))
                call(GetMethod(ContactCardType, books, JmapIds.Result(query.resultOf("/ids")), CARD_PROPERTIES))
            }
        jmapDecoding {
            cards.list.flatMap { card ->
                card.emails
                    .orEmpty()
                    .values
                    .map { MailSender(card.name?.full.orEmpty(), it.address) }
            }
        }
    } catch (expected: JmapException) {
        emptyList()
    }
}

private val CARD_PROPERTIES = listOf("id", "name", "emails")
