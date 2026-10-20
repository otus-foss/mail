package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageMover
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Files a message with one `Email/set` patch of its `mailboxIds` (RFC 8621, section 4.6). Only the
 * named mailboxes change, so the message keeps any other mailboxes it is in. The emails the server
 * moved move in [cache] too.
 */
class JmapMessageMover(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
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
        return left.isNotEmpty() && updateEmail(connections, logins, message, patch, cache)
    }

    override suspend fun moveBack(
        message: MailMessage,
        folderId: String,
        returning: Set<String>,
    ): Boolean {
        val back = returning - folderId - ""
        val patch =
            buildJsonObject {
                if (folderId !in message.location.mailboxIds) put("mailboxIds/${pointer(folderId)}", JsonNull)
                back.forEach { put("mailboxIds/${pointer(it)}", JsonPrimitive(true)) }
            }
        return back.isNotEmpty() && updateEmail(connections, logins, message, patch, cache)
    }

    override suspend fun setInFolder(
        message: MailMessage,
        folderId: String,
        inFolder: Boolean,
    ): Boolean {
        val patch =
            buildJsonObject { put("mailboxIds/${pointer(folderId)}", if (inFolder) JsonPrimitive(true) else JsonNull) }
        return updateEmail(connections, logins, message, patch, cache)
    }

    /** Sends each email's whole `mailboxIds`, so one call files a conversation whose emails differ. */
    override suspend fun setMailboxes(
        accountId: String,
        mailboxes: Map<String, Set<String>>,
    ): Set<String> {
        val patches =
            mailboxes.mapValues { (_, boxes) ->
                buildJsonObject { put("mailboxIds", JsonObject(boxes.associateWith { JsonPrimitive(true) })) }
            }
        return updateAccountEmails(connections, logins, accountId, patches, cache)
    }
}
