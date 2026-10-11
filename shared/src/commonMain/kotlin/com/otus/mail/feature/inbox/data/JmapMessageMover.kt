package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageMover
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
        val patch =
            buildJsonObject {
                put("mailboxIds/$from", JsonNull)
                put("mailboxIds/$folderId", JsonPrimitive(true))
            }
        return from.isNotEmpty() && from != folderId && updateEmail(connections, logins, message, patch)
    }
}
