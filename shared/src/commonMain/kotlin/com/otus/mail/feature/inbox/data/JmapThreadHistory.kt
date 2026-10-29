package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ThreadHistory
import com.otus.mail.feature.inbox.domain.ThreadMessage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Reads a message's thread in one request: `Email/get` for its `threadId`, `Thread/get` for the
 * thread's Emails, and `Email/get` for who sent each and when (RFC 8621, sections 3 and 4.2).
 * Drafts and messages that came after the original are left out.
 */
class JmapThreadHistory(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ThreadHistory {
    override suspend fun earlier(
        accountId: String,
        emailId: String,
    ): List<ThreadMessage> =
        mailCallOr(emptyList()) {
            val account = mailAccount(connections, logins, accountId) ?: return@mailCallOr emptyList()
            val emails = account.threadEmails(emailId, PROPERTIES)
            jmapDecoding { emails.upTo(emailId).map { it.threadMessage() } }
        }

    /** The messages received no later than [emailId], newest first, without drafts. */
    private fun List<EmailObject>.upTo(emailId: String): List<EmailObject> {
        val cutoff = firstOrNull { it.id == emailId }?.receivedAt ?: return emptyList()
        return filter {
            it.keywords[DRAFT_KEYWORD] != true && it.receivedAt <= cutoff
        }.sortedByDescending { it.receivedAt }
    }

    private fun EmailObject.threadMessage(): ThreadMessage {
        val sender = from?.firstOrNull()
        return ThreadMessage(
            id,
            MailSender(sender?.name.orEmpty().ifBlank { sender?.email.orEmpty() }, sender?.email.orEmpty()),
            Instant.parse(receivedAt).toLocalDateTime(zone()).date,
            preview,
        )
    }

    private companion object {
        val PROPERTIES = listOf("id", "from", "receivedAt", "preview", "keywords")
    }
}
