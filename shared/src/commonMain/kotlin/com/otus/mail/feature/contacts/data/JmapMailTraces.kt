package com.otus.mail.feature.contacts.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapCallOr
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.NEWEST_FIRST
import com.otus.mail.core.mail.inMailbox
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mail.received
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.feature.contacts.domain.MailTrace
import com.otus.mail.feature.contacts.domain.MailTraceSource
import com.otus.mail.feature.contacts.domain.MailTraces

/**
 * A login's newest [SCAN] emails in its Sent and Inbox mailboxes (RFC 8621, sections 2 and 4.4),
 * read with only the addresses and dates the frequency scan needs, in batches the server's
 * `maxObjectsInGet` allows. A login without mail or out of reach gives none.
 */
internal class JmapMailTraces(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
) : MailTraceSource {
    override suspend fun logins(): List<String> =
        try {
            connections.savedLogins().map { it.id }
        } catch (expected: LoginStoreException) {
            emptyList()
        }

    override suspend fun traces(loginId: String): MailTraces? =
        jmapCallOr(null) {
            connections.mailAccount(loginId)?.let { account ->
                MailTraces(account.newest(MailboxRole.Sent), account.newest(MailboxRole.Inbox))
            }
        }

    private suspend fun MailAccount.newest(role: MailboxRole): List<MailTrace> {
        val mailbox = mailboxes.of(this).firstOrNull { it.role == role }?.id ?: return emptyList()
        return connection
            .call(QueryMethod(EmailType, id, inMailbox(mailbox), NEWEST_FIRST, SCAN))
            .ids
            .chunked(session.getBatch)
            .flatMap { connection.call(GetMethod(EmailType, id, JmapIds.Of(it), PROPERTIES)).list }
            .mapNotNull(::trace)
    }

    private fun trace(email: EmailObject): MailTrace? {
        val at = email.received ?: return null
        return MailTrace(
            at,
            from = email.from.orEmpty().map { it.email },
            to = email.to.orEmpty().map { it.email },
            copied = (email.cc.orEmpty() + email.bcc.orEmpty()).map { it.email },
        )
    }

    private companion object {
        const val SCAN = 500
        val PROPERTIES = listOf("id", "receivedAt", "from", "to", "cc", "bcc")
    }
}
