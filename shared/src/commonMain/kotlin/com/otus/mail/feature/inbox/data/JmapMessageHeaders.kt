package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.feature.inbox.domain.MailHeader
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageHeaders

/**
 * Reads a message's header fields as the message has them, with `Email/get` of `headers` (RFC 8621,
 * section 4.1.3), for View details. Values lose the space after the colon and their line folds.
 */
class JmapMessageHeaders(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageHeaders {
    override suspend fun headers(message: MailMessage): List<MailHeader>? =
        try {
            mailAccount(connections, logins, message.location.accountId)?.let { account ->
                account.connection
                    .call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(message.emailId)), PROPERTIES))
                    .list
                    .firstOrNull()
                    ?.headers
                    .orEmpty()
                    .map { MailHeader(it.name, it.value.replace(FOLD, " ").trim()) }
            }
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    private companion object {
        val PROPERTIES = listOf("id", "receivedAt", "headers")

        /** A line break and the whitespace that continues a folded header (RFC 5322, section 2.2.3). */
        val FOLD = Regex("""\r?\n[ \t]+""")
    }
}
