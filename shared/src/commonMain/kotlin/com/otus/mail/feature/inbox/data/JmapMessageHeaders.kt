package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailHeader
import com.otus.mail.feature.inbox.domain.MessageHeaders

/**
 * Reads a message's header fields as the message has them, with `Email/get` of `headers` (RFC 8621,
 * section 4.1.3), for View details. Values lose the space after the colon and their line folds.
 */
class JmapMessageHeaders(
    private val connections: JmapConnections,
) : MessageHeaders {
    override suspend fun headers(message: MailMessage): List<MailHeader>? =
        jmapCallOr(null) {
            connections.mailAccount(message.location.accountId)?.let { account ->
                account.connection
                    .call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(message.emailId)), PROPERTIES))
                    .list
                    .firstOrNull()
                    ?.headers
                    .orEmpty()
                    .map { MailHeader(it.name, it.value.replace(FOLD, " ").trim()) }
            }
        }

    private companion object {
        val PROPERTIES = listOf("id", "receivedAt", "headers")

        /** A line break and the whitespace that continues a folded header (RFC 5322, section 2.2.3). */
        val FOLD = Regex("""\r?\n[ \t]+""")
    }
}
