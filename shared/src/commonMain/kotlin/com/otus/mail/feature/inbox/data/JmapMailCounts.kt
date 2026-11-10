package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapFilter
import com.otus.jmap.QueryMethod
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * Counts the rows a query finds on the server, for the filter panel: conversations, or emails in
 * folders that list each email apart, such as Scheduled. The cache can't:
 * it holds only the pages loaded so far.
 */
internal class JmapMailCounts(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
    private val zone: () -> TimeZone,
    private val clock: Clock,
) {
    /** Sums each of [logins]' totals; null when none could be counted. */
    suspend fun count(
        logins: List<JmapLogin>,
        scope: MailScope,
        query: MailQuery,
    ): Long? {
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
        conditions: List<JmapFilter>,
    ): Long? =
        try {
            val account = connections.mailAccount(login)
            val chosen = scope.chosen(mailboxes.of(account), login.id, query)
            if (chosen.isEmpty()) {
                0L
            } else {
                val conversations = chosen.any { it.role?.listsConversations != false }
                val filter = mailFilter(chosen.map { it.id }, conditions)
                account.connection
                    .call(QueryMethod(EmailType, account.id, filter, limit = 0, extra = count(conversations)))
                    .total
            }
        } catch (expected: JmapException) {
            // A login that cannot be counted leaves the others' sum.
            null
        }

    private companion object {
        /** Only the number of matching conversations or emails (RFC 8620, section 5.5; RFC 8621, section 4.4.3). */
        fun count(conversations: Boolean) =
            mapOf("calculateTotal" to JsonPrimitive(true), "collapseThreads" to JsonPrimitive(conversations))
    }
}
