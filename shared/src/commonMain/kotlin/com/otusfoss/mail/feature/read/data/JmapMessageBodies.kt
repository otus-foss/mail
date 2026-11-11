package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.core.jmap.jmapDecoding
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.DELIVERED_TO_PROPERTY
import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.LIST_ID_PROPERTY
import com.otusfoss.mail.core.mail.LIST_UNSUBSCRIBE_PROPERTY
import com.otusfoss.mail.core.mail.ORIGINAL_TO_PROPERTY
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.objects
import com.otusfoss.mail.core.mail.putObject
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.data.receivedMillis
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageBodies
import com.otusfoss.mail.core.messages.domain.MessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.CoroutineContext

/**
 * Loads one message's `textBody` and `htmlBody` parts and their values with `Email/get` (RFC 8621,
 * sections 4.1.4 and 4.2), up to [MAX_BODY_BYTES] per part, with its files and list headers. The
 * list loads only previews, so the body comes when a message opens. An email's body never changes,
 * so [cache] keeps the fetched Email object, with only these properties, under its id per login;
 * the message opens again at once and offline. It is saved with its received time, so trimming old
 * bodies can drop it. Images the HTML shows by `cid:` are downloaded on each open, since the cache
 * keeps only the Email object. Reading the body, up to a megabyte of HTML per part, and inlining its
 * images runs on [parsing], off the caller's main thread.
 */
class JmapMessageBodies(
    private val connections: JmapConnections,
    private val cache: MailCache = InMemoryMailCache(),
    private val parsing: CoroutineContext = Dispatchers.Default,
) : MessageBodies {
    override suspend fun content(message: MailMessage): MessageContent? =
        withContext(parsing) {
            jmapCallOr(null) {
                val loginId = message.location.accountId
                cache.withAccount(loginId) { accountCache ->
                    val email = accountCache.cached(loginId, message.emailId) ?: accountCache.fetched(loginId, message)
                    email?.let { found ->
                        val content = jmapDecoding { found.toMessageContent() }
                        withImages(loginId, content, found.inlineParts(content.html?.html))
                    }
                }
            }
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
                connections.mailAccount(loginId)?.withInlineImages(content, parts) ?: content
            } catch (expected: JmapException) {
                content
            }
        }

    private suspend fun MailCache.cached(
        loginId: String,
        emailId: String,
    ): EmailObject? =
        objects(loginId, CacheKind.Body, EmailObject.serializer(), listOf(emailId))[emailId]
            // Bodies saved before files were fetched have no attachments list; fetch those again.
            ?.takeIf { it.attachments != null }

    /** The email from the server, saved in [cache] for the next open. */
    private suspend fun MailCache.fetched(
        loginId: String,
        message: MailMessage,
    ): EmailObject? {
        val account = connections.mailAccount(loginId) ?: return null
        val email =
            account.connection
                .call(GetMethod(EmailType, account.id, JmapIds.Of(listOf(message.emailId)), PROPERTIES, ARGUMENTS))
                .list
                .firstOrNull()
        if (email != null) {
            try {
                write(loginId) {
                    putObject(CacheKind.Body, email.id, EmailObject.serializer(), email, email.receivedMillis)
                    // The body may hold markup the headers lacked, so the Inbox sorts the email again.
                    remove(CacheKind.Category, listOf(email.id))
                }
            } catch (expected: CacheStorageException) {
                // The fetched body can still be opened without a persistent copy.
            }
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
                LIST_UNSUBSCRIBE_PROPERTY,
                "header:List-Unsubscribe-Post:asText",
                LIST_ID_PROPERTY,
                DELIVERED_TO_PROPERTY,
                ORIGINAL_TO_PROPERTY,
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
