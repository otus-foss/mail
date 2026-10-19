package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.anyOf
import com.otus.mail.core.mail.condition
import com.otus.mail.feature.inbox.domain.AddressBook
import com.otus.mail.feature.inbox.domain.FoundPerson
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.PeopleField
import com.otus.mail.feature.inbox.domain.PeopleSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds people by searching each login's mail on the server (RFC 8621, section 4.4): the newest
 * [limit] conversations whose From, or To and Cc, header holds the text, one email each, then
 * every distinct address in those headers. [contacts] adds the people the address book knows.
 * Logins search in parallel, and one that fails adds nobody.
 */
class JmapPeopleSearch(
    private val connections: JmapConnections,
    private val logins: LoginStore,
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
            MailAccounts(connections, logins).logins().map { it.id }.filter { chosen == null || it in chosen }
        } catch (expected: LoginStoreException) {
            emptyList()
        }

    private suspend fun mail(
        loginId: String,
        field: PeopleField,
        text: String,
    ): List<FoundPerson> =
        try {
            mailAccount(connections, logins, loginId)
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
        } catch (expected: JmapException) {
            emptyList()
        } catch (expected: LoginStoreException) {
            emptyList()
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
        .groupBy { (address) -> address.email.lowercase() }
        .values
        .map { seen ->
            val name = seen.firstNotNullOfOrNull { (address) -> address.name?.ifBlank { null } }.orEmpty()
            FoundPerson(
                MailSender(name, seen.first().first.email),
                conversations = seen.size,
                lastReceived = seen.maxOf { (_, received) -> received },
            )
        }

private fun PeopleField.filter(text: String): JsonObject =
    when (this) {
        PeopleField.From -> condition("from", JsonPrimitive(text))
        PeopleField.To -> anyOf(listOf("to", "cc").map { name -> condition(name, JsonPrimitive(text)) })
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
