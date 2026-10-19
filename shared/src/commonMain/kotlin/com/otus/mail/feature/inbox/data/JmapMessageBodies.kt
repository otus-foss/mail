package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapIds
import com.otus.jmap.jmapDecoding
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.objects
import com.otus.mail.core.mail.putObject
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageBodies
import com.otus.mail.feature.inbox.domain.MessageContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Loads one message's `textBody` and `htmlBody` parts and their values with `Email/get` (RFC 8621,
 * sections 4.1.4 and 4.2), up to [MAX_BODY_BYTES] per part, with its files and list headers. The
 * list loads only previews, so the body comes when a message opens. An email's body never changes,
 * so [cache] keeps the fetched Email object, with only these properties, under its id per login;
 * the message opens again at once and offline. It is saved with its received time, so trimming old
 * bodies can drop it. Images the HTML shows by `cid:` are downloaded on each open, since the cache
 * keeps only the Email object.
 */
class JmapMessageBodies(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
) : MessageBodies {
    override suspend fun content(message: MailMessage): MessageContent? =
        try {
            val loginId = message.location.accountId
            val email = cached(loginId, message.emailId) ?: fetched(loginId, message)
            email?.let { found ->
                val content = jmapDecoding { found.toMessageContent() }
                withImages(loginId, content, found.inlineParts(content.html?.html))
            }
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }

    /** Offline, the message still opens; only its `cid:` images are missing. */
    private suspend fun withImages(
        loginId: String,
        content: MessageContent,
        parts: List<EmailBodyPart>,
    ): MessageContent =
        if (parts.isEmpty()) {
            content
        } else {
            try {
                mailAccount(connections, logins, loginId)?.withInlineImages(content, parts) ?: content
            } catch (expected: JmapException) {
                content
            }
        }

    private suspend fun cached(
        loginId: String,
        emailId: String,
    ): EmailObject? =
        cache
            .objects(loginId, CacheKind.Body, EmailObject.serializer(), listOf(emailId))[emailId]
            // Bodies saved before files were fetched have no attachments list; fetch those again.
            ?.takeIf { it.attachments != null }

    /** The email from the server, saved in [cache] for the next open. */
    private suspend fun fetched(
        loginId: String,
        message: MailMessage,
    ): EmailObject? {
        val account = mailAccount(connections, logins, loginId) ?: return null
        val ids = JmapIds.Of(listOf(message.emailId))
        val email =
            account.connection
                .call(
                    GetMethod(EmailType, account.id, ids, PROPERTIES, ARGUMENTS),
                ).list
                .firstOrNull()
        if (email != null) {
            val received = runCatching { Instant.parse(email.receivedAt).toEpochMilliseconds() }.getOrNull()
            cache.write(loginId) { putObject(CacheKind.Body, email.id, EmailObject.serializer(), email, received) }
        }
        return email
    }

    private companion object {
        const val MAX_BODY_BYTES = 1_000_000

        val PROPERTIES =
            listOf(
                "id",
                "receivedAt",
                "textBody",
                "htmlBody",
                "bodyValues",
                "attachments",
                "header:List-Unsubscribe:asURLs",
                "header:List-Unsubscribe-Post:asText",
                "header:List-Id:asText",
                "header:Delivered-To:asAddresses:all",
                "header:X-Original-To:asAddresses:all",
            )

        /** Each part's `type` tells an HTML part from a plain one; the rest name and fetch files. */
        val ARGUMENTS =
            mapOf(
                "bodyProperties" to
                    JsonArray(
                        listOf("partId", "type", "blobId", "size", "name", "cid", "disposition").map(::JsonPrimitive),
                    ),
                "fetchAllBodyValues" to JsonPrimitive(true),
                "maxBodyValueBytes" to JsonPrimitive(MAX_BODY_BYTES),
            )
    }
}
