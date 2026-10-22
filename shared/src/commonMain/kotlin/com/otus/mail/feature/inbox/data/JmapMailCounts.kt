package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * Counts the conversations a query finds on the server, for the filter panel. The cache can't:
 * it holds only the pages loaded so far.
 */
internal class JmapMailCounts(
    private val connections: JmapConnections,
    private val zone: () -> TimeZone,
    private val clock: Clock,
) {
    /** Each login's mailboxes for the counts of one filter panel session. */
    private var mailboxes: Map<String, List<MailboxObject>> = emptyMap()

    /** Starts a new session: the next counts read each login's mailboxes again. */
    fun begin() {
        mailboxes = emptyMap()
    }

    /**
     * Sums each of [logins]' totals; null when none could be counted, or when a choice only the
     * loaded threads can tell is on. Each login's mailboxes load once until [begin].
     */
    suspend fun count(
        logins: List<JmapLogin>,
        scope: MailScope,
        query: MailQuery,
    ): Long? {
        if (query.filters.clientOnly) return null
        val now = clock.now()
        val totals =
            coroutineScope {
                logins
                    .mapNotNull { login -> emailConditions(query, login.id, now, zone())?.let { login to it } }
                    .map { (login, conditions) -> async { countLogin(login, scope, query, conditions) } }
                    .awaitAll()
            }
        return totals.filterNotNull().takeIf { it.isNotEmpty() || totals.isEmpty() }?.sum()
    }

    private suspend fun countLogin(
        login: JmapLogin,
        scope: MailScope,
        query: MailQuery,
        conditions: List<JsonObject>,
    ): Long? =
        try {
            val connection = connections.connection(login)
            connection.session().accountFor(JmapCapability.MAIL)?.let { accountId ->
                val known =
                    mailboxes[login.id] ?: mailboxesOf(connection, accountId).also { mailboxes += login.id to it }
                val ids = scope.chosen(known, login.id, query).map { it.id }
                if (ids.isEmpty()) {
                    0L
                } else {
                    connection
                        .call(QueryMethod(EmailType, accountId, mailFilter(ids, conditions), limit = 0, extra = COUNT))
                        .total
                }
            }
        } catch (expected: JmapException) {
            // A login that cannot be counted leaves the others' sum.
            null
        }

    private suspend fun mailboxesOf(
        connection: JmapConnection,
        accountId: String,
    ) = connection.call(GetMethod(MailboxType, accountId, properties = listOf("id", "name", "role"))).list

    private companion object {
        /** Only the number of matching conversations (RFC 8620, section 5.5; RFC 8621, section 4.4.3). */
        val COUNT = mapOf("calculateTotal" to JsonPrimitive(true), "collapseThreads" to JsonPrimitive(true))
    }
}
