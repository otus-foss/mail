package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.feature.contacts.domain.MailTrace
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.MailTraces
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * A login's newest [SCAN] emails in its Sent and Inbox mailboxes (RFC 8621, sections 2 and 4.4),
 * read with only the addresses and dates the frequency scan needs, in batches the server's
 * `maxObjectsInGet` allows. A login without mail or out of reach gives none.
 */
internal class JmapMailTraces(
    connections: JmapConnections,
    logins: LoginStore,
) : MailTraceSource {
    private val accounts = MailAccounts(connections, logins)

    override suspend fun logins(): List<String> =
        try {
            accounts.logins().map { it.id }
        } catch (expected: LoginStoreException) {
            emptyList()
        }

    override suspend fun traces(loginId: String): MailTraces? =
        mailCallOr(null) {
            accounts.open(loginId)?.let { account ->
                MailTraces(account.newest(SENT_ROLE), account.newest(INBOX_ROLE))
            }
        }

    private suspend fun MailAccount.newest(role: String): List<MailTrace> {
        val mailbox =
            connection.call(QueryMethod(MailboxType, id, buildJsonObject { put("role", role) })).ids.firstOrNull()
                ?: return emptyList()
        val ids = connection.call(QueryMethod(EmailType, id, inMailbox(mailbox), NEWEST_FIRST, SCAN)).ids
        val batch = (session.maxObjectsInGet ?: GET_BATCH.toLong()).toInt().coerceIn(1, GET_BATCH)
        return ids
            .chunked(batch)
            .flatMap { connection.call(GetMethod(EmailType, id, JmapIds.Of(it), PROPERTIES)).list }
            .mapNotNull(::trace)
    }

    private fun trace(email: EmailObject): MailTrace? {
        val at = runCatching { Instant.parse(email.receivedAt) }.getOrNull() ?: return null
        return MailTrace(
            at,
            from = email.from.orEmpty().map { it.email },
            to = email.to.orEmpty().map { it.email },
            copied = (email.cc.orEmpty() + email.bcc.orEmpty()).map { it.email },
        )
    }

    private companion object {
        const val SCAN = 500

        /** Read when the server names no `maxObjectsInGet`; small enough for any server. */
        const val GET_BATCH = 200
        const val SENT_ROLE = "sent"
        const val INBOX_ROLE = "inbox"
        val PROPERTIES = listOf("id", "receivedAt", "from", "to", "cc", "bcc")
    }
}
