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
import com.otus.mail.feature.inbox.domain.matching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Suggests the account's contact cards (RFC 9610, section 3), then the people of its newest mail
 * (RFC 8621, section 4.4), most frequent first. Server search matches whole words only, so it
 * loads both lists once per [maxAge] and matches the typed text on the device.
 */
class JmapAddressBook(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val limit: Int = DEFAULT_LIMIT,
    private val maxAge: Duration = 5.minutes,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : AddressBook {
    private val lock = Mutex()
    private var known: Map<String, Known> = emptyMap()

    override suspend fun suggestions(
        accountId: String,
        query: String,
    ): List<MailSender> =
        people(accountId)
            .matching(query)
            .take(limit)

    private suspend fun people(accountId: String): List<MailSender> =
        lock.withLock {
            known[accountId]?.takeIf { it.loadedAt.elapsedNow() < maxAge }?.people
                ?: load(accountId)?.also { known += accountId to Known(it, timeSource.markNow()) }
                ?: emptyList()
        }

    private suspend fun load(accountId: String): List<MailSender>? =
        try {
            mailAccount(connections, logins, accountId)?.let { account ->
                (contacts(account) + correspondents(account))
                    .distinctBy { it.address.lowercase() }
                    .map { it.copy(accountKey = accountId) }
            }
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    /** Empty when the server has no contacts or cannot list them, so mail still suggests people. */
    private suspend fun contacts(account: MailAccount): List<MailSender> {
        val books = account.session.accountFor(JmapCapability.CONTACTS) ?: return emptyList()
        return try {
            val cards =
                account.connection.request {
                    val query = call(QueryMethod(ContactCardType, books, limit = CONTACT_SCAN))
                    call(GetMethod(ContactCardType, books, JmapIds.Result(query.resultOf("/ids")), CARD_PROPERTIES))
                }
            jmapDecoding {
                cards.list.flatMap { card ->
                    card.emails.values.map { MailSender(card.name?.full.orEmpty(), it.address) }
                }
            }
        } catch (expected: JmapException) {
            emptyList()
        }
    }

    private suspend fun correspondents(account: MailAccount): List<MailSender> {
        val emails =
            account.connection.request {
                val query = call(QueryMethod(EmailType, account.id, sort = NEWEST_FIRST, limit = MAIL_SCAN))
                call(GetMethod(EmailType, account.id, JmapIds.Result(query.resultOf("/ids")), EMAIL_PROPERTIES))
            }
        return jmapDecoding {
            emails.list
                .flatMap { it.from.orEmpty() + it.to.orEmpty() + it.cc.orEmpty() }
                .filter { it.email.isNotBlank() }
                .groupBy { it.email.lowercase() }
                .values
                .sortedByDescending { it.size }
                .map { seen ->
                    MailSender(seen.firstNotNullOfOrNull { it.name?.ifBlank { null } }.orEmpty(), seen.first().email)
                }
        }
    }

    private class Known(
        val people: List<MailSender>,
        val loadedAt: TimeMark,
    )

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val CONTACT_SCAN = 500
        const val MAIL_SCAN = 200
        val CARD_PROPERTIES = listOf("id", "name", "emails")
        val EMAIL_PROPERTIES = listOf("id", "receivedAt", "from", "to", "cc")
    }
}
