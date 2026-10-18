package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.MUTED_KEYWORD
import com.otus.mail.core.mail.objects
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Mutes a conversation by setting [MUTED_KEYWORD] on every email of its thread with `Email/set`
 * (RFC 8621, sections 4.1.1 and 4.6), so another device sees it too. It reads the whole thread
 * first, since the row lists only the emails in its folder. The emails it changed change in [cache].
 */
class JmapConversationMute(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
) : ConversationMute {
    override suspend fun isMuted(message: MailMessage): Boolean =
        thread(message)?.let { emails -> emails.any { it.keywords[MUTED_KEYWORD] == true } }
            ?: cachedMuted(message)

    override suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean {
        val ids = thread(message)?.map { it.id } ?: return false
        val patch = buildJsonObject { put("keywords/$MUTED_KEYWORD", if (muted) JsonPrimitive(true) else JsonNull) }
        val updated =
            updateAccountEmails(connections, logins, message.location.accountId, ids.associateWith { patch }, cache)
        return updated.containsAll(ids)
    }

    /** The thread's emails with their keywords; null when the server could not be read. */
    private suspend fun thread(message: MailMessage): List<EmailObject>? {
        if (message.emailId.isEmpty()) return null
        return try {
            mailAccount(connections, logins, message.location.accountId)
                ?.threadEmails(message.emailId, PROPERTIES)
                ?.ifEmpty { null }
        } catch (expected: JmapException) {
            null
        } catch (expected: LoginStoreException) {
            null
        }
    }

    /** Offline, the list's copies of the row's emails answer. */
    private suspend fun cachedMuted(message: MailMessage): Boolean {
        val loginId = message.location.accountId
        return listOf(CacheKind.Email, CacheKind.Member).any { kind ->
            cache
                .objects(loginId, kind, EmailObject.serializer(), message.memberIds)
                .values
                .any { it.keywords[MUTED_KEYWORD] == true }
        }
    }

    private companion object {
        val PROPERTIES = listOf("id", "receivedAt", "keywords")
    }
}
