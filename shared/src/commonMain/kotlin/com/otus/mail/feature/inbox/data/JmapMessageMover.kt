package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.EmailType
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Moves a message with one `Email/set` patch (RFC 8621, section 4.6): it leaves the mailbox it is
 * shown in and joins the target, keeping any other mailboxes it is also in.
 */
class JmapMessageMover(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageMover {
    override suspend fun move(
        message: MailMessage,
        folderId: String,
    ): Boolean {
        val from = message.location.mailboxId
        val movable = message.emailId.isNotEmpty() && from.isNotEmpty() && from != folderId
        return movable &&
            try {
                send(message, folderId)
            } catch (expected: JmapException) {
                false
            } catch (expected: LoginStoreException) {
                false
            }
    }

    private suspend fun send(
        message: MailMessage,
        folderId: String,
    ): Boolean {
        val login = withContext(Dispatchers.IO) { logins.logins() }.firstOrNull { it.id == message.location.accountId }
        val connection = login?.let { connections.connection(it) }
        val accountId = connection?.session()?.accountFor(JmapCapability.MAIL)
        if (connection == null || accountId == null) return false
        val patch =
            buildJsonObject {
                put("mailboxIds/${message.location.mailboxId}", JsonNull)
                put("mailboxIds/$folderId", JsonPrimitive(true))
            }
        val result = connection.call(SetMethod(EmailType, accountId, update = mapOf(message.emailId to patch)))
        return message.emailId in result.updated
    }
}
