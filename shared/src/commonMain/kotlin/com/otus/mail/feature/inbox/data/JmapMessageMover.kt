package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageMover
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Files a message with one `Email/set` patch of its `mailboxIds` (RFC 8621, section 4.6). Only the
 * named mailboxes change, so the message keeps any other mailboxes it is in.
 */
class JmapMessageMover(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : MessageMover {
    override suspend fun move(
        message: MailMessage,
        folderId: String,
        leaving: Set<String>,
    ): Boolean {
        val left = leaving - folderId - ""
        val patch =
            buildJsonObject {
                left.forEach { put("mailboxIds/${pointer(it)}", JsonNull) }
                put("mailboxIds/${pointer(folderId)}", JsonPrimitive(true))
            }
        return left.isNotEmpty() && updateEmail(connections, logins, message, patch)
    }

    override suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean {
        val patch =
            buildJsonObject { put("mailboxIds/${pointer(folderId)}", if (inFolder) JsonPrimitive(true) else JsonNull) }
        return updateEmail(connections, logins, message, patch)
    }
}
