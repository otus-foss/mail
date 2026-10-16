package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageBodies
import com.otus.mail.feature.inbox.domain.MessageHtml
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Loads one message's `htmlBody` parts and their values with `Email/get` (RFC 8621, sections
 * 4.1.4 and 4.2), up to [MAX_HTML_BYTES] per part. The list loads only short text bodies, so the
 * HTML comes when a message opens.
 */
class JmapMessageBodies(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageBodies {
    override suspend fun html(message: MailMessage): MessageHtml? =
        try {
            val account = mailAccount(connections, logins, message.location.accountId)
            account
                ?.connection
                ?.call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(message.emailId)), PROPERTIES, ARGUMENTS))
                ?.list
                ?.firstOrNull()
                ?.toMessageHtml()
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    private companion object {
        const val MAX_HTML_BYTES = 1_000_000

        val PROPERTIES = listOf("id", "receivedAt", "htmlBody", "bodyValues")

        val ARGUMENTS =
            mapOf(
                "bodyProperties" to JsonArray(listOf("partId", "type").map(::JsonPrimitive)),
                "fetchHTMLBodyValues" to JsonPrimitive(true),
                "maxBodyValueBytes" to JsonPrimitive(MAX_HTML_BYTES),
            )
    }
}
