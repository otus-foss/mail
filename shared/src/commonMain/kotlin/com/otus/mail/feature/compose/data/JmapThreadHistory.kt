package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.jmapCallOr
import com.otus.jmap.jmapDecoding
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.isDraft
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.data.threadEmails
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.feature.compose.domain.ThreadHistory
import com.otus.mail.feature.compose.domain.ThreadMessage
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
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ThreadHistory {
    override suspend fun earlier(
        accountId: String,
        emailId: String,
    ): List<ThreadMessage> =
        jmapCallOr(emptyList()) {
            connections
                .mailAccount(accountId)
                ?.let { account ->
                    val emails = account.threadEmails(emailId, PROPERTIES)
                    jmapDecoding { emails.upTo(emailId).map { it.threadMessage() } }
                }.orEmpty()
        }

    /**
     * The messages received no later than [emailId], newest first, without drafts. Times compare as
     * instants, since a server may write some with fractions of a second and others without.
     */
    private fun List<EmailObject>.upTo(emailId: String): List<EmailObject> {
        val received = associate { it.id to Instant.parse(it.receivedAt) }
        val cutoff = received[emailId] ?: return emptyList()
        return filter { !it.isDraft && received.getValue(it.id) <= cutoff }
            .sortedByDescending { received.getValue(it.id) }
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
