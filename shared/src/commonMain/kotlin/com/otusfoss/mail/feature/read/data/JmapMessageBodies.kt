package com.otusfoss.mail.feature.read.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapCapability
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.JmapType
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
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.CoroutineContext

/**
 * Loads one message's `textBody` and `htmlBody` parts and their values with `Email/get` (RFC 8621,
 * sections 4.1.4 and 4.2), up to [MAX_BODY_BYTES] per part, with its files and list headers. The
 * list loads only previews, so the body comes when a message opens. An email's body never changes,
 * so [cache] keeps the fetched Email object, with only these properties, under its id per login;
 * the message opens again at once and offline. The save runs on [saving] and outlives the open.
 * It is saved with its received time, so trimming old bodies can drop it. Images the HTML shows by
 * `cid:` are downloaded on each open, since the cache keeps only the Email object, so the body
 * comes first and again once they arrive. Reading the body, up to a megabyte of HTML per part, and
 * inlining its images runs on [parsing], off the caller's main thread.
 */
class JmapMessageBodies(
    private val connections: JmapConnections,
    private val cache: MailCache = InMemoryMailCache(),
    private val parsing: CoroutineContext = Dispatchers.Default,
    private val saving: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MessageBodies {
    override fun content(message: MailMessage): Flow<MessageContent?> =
        flow {
            val loginId = message.location.accountId
            val email =
                jmapCallOr(null) {
                    cache.withAccount(loginId) { it.cached(loginId, message.emailId) ?: it.fetched(loginId, message) }
                }
            // Bodies saved before the subject was fetched take it from the list.
            val body =
                email
                    ?.let { found -> if (found.subject == null) found.copy(subject = message.subject) else found }
                    ?.let { found -> jmapCallOr(null) { jmapDecoding { found.toMessageContent() } } }
            emit(body)
            if (email == null || body == null) return@flow
            val parts = email.inlineParts(body.html?.html)
            if (parts.isNotEmpty()) emit(withImages(loginId, body, parts))
        }.flowOn(parsing)

    override suspend fun prefetch(messages: List<MailMessage>) {
        messages
            .filter { it.emailId.isNotEmpty() && !isDeviceDraftId(it.emailId) }
            .groupBy({ it.location.accountId }, { it.emailId })
            .forEach { (loginId, emailIds) ->
                try {
                    jmapCallOr(Unit) { cache.withAccount(loginId) { it.prefetch(loginId, emailIds.distinct()) } }
                } catch (expected: CacheStorageException) {
                    // A cache that cannot be read now leaves the bodies to load when they open.
                }
            }
    }

    /**
     * Fetches and saves the first [PREFETCH_COUNT] bodies of [emailIds] the cache lacks. The server
     * is asked for their sizes first, and only bodies up to [PREFETCH_BYTES] come, so a list of
     * large mail costs little data. They come a few at a time and each batch is saved as it
     * arrives, so a prefetch cut short, as when the list changes, keeps what it got.
     */
    private suspend fun MailCache.prefetch(
        loginId: String,
        emailIds: List<String>,
    ) {
        val missing = (emailIds - read(loginId, CacheKind.Body, emailIds).keys).take(PREFETCH_COUNT)
        val account = missing.takeIf { it.isNotEmpty() }?.let { connections.mailAccount(loginId) } ?: return
        account.connection
            .call(GetMethod(EmailSizeType, account.id, JmapIds.Of(missing), SIZE_PROPERTIES))
            .list
            .filter { it.size in 1..PREFETCH_BYTES }
            .map { it.id }
            .chunked(minOf(account.session.getBatch, PREFETCH_BATCH))
            .forEach { batch ->
                val emails =
                    account.connection
                        .call(GetMethod(EmailType, account.id, JmapIds.Of(batch), PROPERTIES, ARGUMENTS))
                        .list
                saving.launch { save(loginId, emails) }
            }
    }

    /** Offline, the message still opens; only its `cid:` images are missing. */
    private suspend fun withImages(
        loginId: String,
        content: MessageContent,
        parts: List<EmailBodyPart>,
    ): MessageContent =
        jmapCallOr(content) {
            connections.mailAccount(loginId)?.withInlineImages(content, parts) ?: content
        }

    private suspend fun MailCache.cached(
        loginId: String,
        emailId: String,
    ): EmailObject? =
        objects(loginId, CacheKind.Body, EmailObject.serializer(), listOf(emailId))[emailId]
            // Bodies saved before files were fetched have no attachments list; fetch those again.
            ?.takeIf { it.attachments != null }

    /**
     * The email from the server. It is saved in [cache] for the next open on [saving], after it is
     * returned: the write can wait behind a list sync, and the reader should not.
     */
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
        if (email != null) saving.launch { save(loginId, listOf(email)) }
        return email
    }

    /** Keeps [emails] for their next open. A removed account's cache refuses them, which ends the save. */
    private suspend fun MailCache.save(
        loginId: String,
        emails: List<EmailObject>,
    ) {
        try {
            write(loginId) {
                emails.forEach { putObject(CacheKind.Body, it.id, EmailObject.serializer(), it, it.receivedMillis) }
                // A body may hold markup the headers lacked, so the Inbox sorts the email again.
                remove(CacheKind.Category, emails.map { it.id })
            }
        } catch (expected: CacheStorageException) {
            // A fetched body can still be opened without a persistent copy.
        }
    }

    /** An email's size in octets, all that [prefetch] asks of an email before it fetches its body. */
    @Serializable
    private class EmailSize(
        val id: String,
        val size: Long = 0,
    )

    private companion object {
        const val MAX_BODY_BYTES = 1_000_000

        /** How many listed bodies one prefetch brings at most, about a screen and a half of rows. */
        const val PREFETCH_COUNT = 20

        /** How many bodies one prefetch request brings. */
        const val PREFETCH_BATCH = 5

        /** The largest message a prefetch brings; most mail without files is far smaller. */
        const val PREFETCH_BYTES = 256_000L

        val EmailSizeType = JmapType("Email", JmapCapability.MAIL, EmailSize.serializer())
        val SIZE_PROPERTIES = listOf("id", "size")

        val PROPERTIES =
            listOf(
                "id",
                "receivedAt",
                "subject",
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
