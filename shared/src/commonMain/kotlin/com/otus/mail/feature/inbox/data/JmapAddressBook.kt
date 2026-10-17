package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.ContactCardType
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.MailSender
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Suggests contacts whose card matches the text (RFC 9610, `ContactCard/query` with a `text`
 * filter), then people in the From and To of the account's newest mail that matches it (RFC 8621,
 * section 4.4). Both go in one request where the server offers contacts in the same session.
 */
class JmapAddressBook(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
) : AddressBook {
    override suspend fun suggestions(
        accountId: String,
        query: String,
    ): List<MailSender> {
        val text = query.trim()
        if (text.length < MIN_QUERY) return emptyList()
        return try {
            mailAccount(connections, logins, accountId)
                ?.let { account ->
                    (contacts(account, text) + correspondents(account, text)).distinctBy { it.address.lowercase() }
                }.orEmpty()
                .map { it.copy(accountKey = accountId) }
                .take(limit)
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
        }
    }

    private suspend fun contacts(
        account: MailAccount,
        text: String,
    ): List<MailSender> {
        val books = account.session.accountFor(JmapCapability.CONTACTS) ?: return emptyList()
        val cards =
            account.connection.request {
                val filter = buildJsonObject { put("text", JsonPrimitive(text)) }
                val query = call(QueryMethod(ContactCardType, books, filter, limit = limit))
                call(GetMethod(ContactCardType, books, JmapIds.Result(query.resultOf("/ids")), CARD_PROPERTIES))
            }
        return jmapDecoding {
            cards.list.flatMap { card -> card.emails.values.map { MailSender(card.name?.full.orEmpty(), it.address) } }
        }
    }

    private suspend fun correspondents(
        account: MailAccount,
        text: String,
    ): List<MailSender> {
        val emails =
            account.connection.request {
                val filter =
                    buildJsonObject {
                        put("operator", "OR")
                        putJsonArray("conditions") {
                            addJsonObject { put("from", text) }
                            addJsonObject { put("to", text) }
                        }
                    }
                val query = call(QueryMethod(EmailType, account.id, filter, NEWEST_FIRST, MAIL_SCAN))
                call(GetMethod(EmailType, account.id, JmapIds.Result(query.resultOf("/ids")), EMAIL_PROPERTIES))
            }
        val needle = text.lowercase()
        return jmapDecoding {
            emails.list
                .flatMap { it.from.orEmpty() + it.to.orEmpty() }
                .filter { needle in it.email.lowercase() || needle in it.name.orEmpty().lowercase() }
                .map { MailSender(it.name.orEmpty(), it.email) }
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 6
        const val MIN_QUERY = 2
        const val MAIL_SCAN = 30
        val CARD_PROPERTIES = listOf("id", "name", "emails")
        val EMAIL_PROPERTIES = listOf("id", "from", "to")
    }
}
