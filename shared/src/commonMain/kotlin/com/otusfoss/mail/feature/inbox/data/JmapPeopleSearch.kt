package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapFilter
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.anyOf
import com.otusfoss.core.jmap.filterCondition
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.jmapDecoding
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailAddressKey
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.NEWEST_FIRST
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.AddressBook
import com.otusfoss.mail.core.messages.domain.FoundPerson
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.PeopleField
import com.otusfoss.mail.core.messages.domain.PeopleSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds people by searching each login's mail on the server (RFC 8621, section 4.4): the newest
 * [limit] conversations whose From, or To and Cc, header holds the text, one email each, then
 * every distinct address in those headers. [contacts] adds the people the address book knows.
 * Logins search in parallel, and one that fails adds nobody.
 */
class JmapPeopleSearch(
    private val connections: JmapConnections,
    private val contacts: AddressBook,
    private val limit: Int = DEFAULT_LIMIT,
) : PeopleSearch {
    override suspend fun find(
        field: PeopleField,
        text: String,
        logins: Set<String>?,
    ): List<FoundPerson> {
        val wanted = text.trim()
        val ids = if (wanted.isEmpty()) emptyList() else loginIds(logins)
        return coroutineScope {
            ids
                .map { id -> async { mail(id, field, wanted) + contacts.suggestions(id, wanted).map(::FoundPerson) } }
                .awaitAll()
                .flatten()
        }
    }

    /** The ids of [chosen] logins, or of every login when null; none when the logins cannot be read. */
    private suspend fun loginIds(chosen: Set<String>?): List<String> =
        try {
            connections.savedLogins().map { it.id }.filter { chosen == null || it in chosen }
        } catch (expected: LoginStoreException) {
            emptyList()
        }

    private suspend fun mail(
        loginId: String,
        field: PeopleField,
        text: String,
    ): List<FoundPerson> =
        jmapCallOr(emptyList()) {
            connections
                .mailAccount(loginId)
                ?.let { account ->
                    val emails =
                        account.connection.request {
                            val filter = field.filter(text)
                            val query = call(QueryMethod(EmailType, account.id, filter, NEWEST_FIRST, limit, COLLAPSED))
                            val ids = JmapIds.Result(query.resultOf("/ids"))
                            call(GetMethod(EmailType, account.id, ids, field.properties))
                        }
                    jmapDecoding { people(emails.list, field) }
                }.orEmpty()
        }

    private companion object {
        const val DEFAULT_LIMIT = 20
        val COLLAPSED = mapOf("collapseThreads" to JsonPrimitive(true))
    }
}

/** Each address of [emails]' [field] headers once, with how many of them name it and the newest. */
private fun people(
    emails: List<EmailObject>,
    field: PeopleField,
): List<FoundPerson> =
    emails
        .flatMap { email -> field.addresses(email).map { it to email.receivedAt } }
        .filter { (address) -> address.email.isNotBlank() }
        .groupBy { (address) -> EmailAddressKey(address.email) }
        .values
        .map { seen ->
            val name = seen.firstNotNullOfOrNull { (address) -> address.name?.ifBlank { null } }.orEmpty()
            FoundPerson(
                MailSender(name, seen.first().first.email),
                conversations = seen.size,
                lastReceived = seen.maxOf { (_, received) -> received },
            )
        }

private fun PeopleField.filter(text: String): JmapFilter =
    when (this) {
        PeopleField.From -> filterCondition("from", JsonPrimitive(text))
        PeopleField.To -> anyOf(listOf("to", "cc").map { name -> filterCondition(name, JsonPrimitive(text)) })
    }

private val PeopleField.properties: List<String>
    get() =
        when (this) {
            PeopleField.From -> listOf("id", "receivedAt", "from")
            PeopleField.To -> listOf("id", "receivedAt", "to", "cc")
        }

private fun PeopleField.addresses(email: EmailObject): List<EmailAddress> =
    when (this) {
        PeopleField.From -> email.from.orEmpty()
        PeopleField.To -> email.to.orEmpty() + email.cc.orEmpty()
    }
