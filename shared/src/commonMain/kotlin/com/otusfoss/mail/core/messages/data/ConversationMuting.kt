package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.GetMethod
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapIds
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.isMuted
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.mail.objects
import com.otusfoss.mail.core.messages.domain.MailMessage

/**
 * Which emails a mute of a conversation marks, and whether it is muted. Muting sets the keyword
 * on every email of the thread, read from the server, since the row lists only the emails in its
 * folder; offline the row's own emails stand in. Muting marks every email, so reading the
 * keywords of the oldest ones that fit one `Email/get` tells whether it is muted.
 */
internal class ConversationMuting(
    private val connections: JmapConnections,
    private val cache: MailCache,
) {
    suspend fun isMuted(message: MailMessage): Boolean =
        fromServer(message) { account, ids ->
            val oldest = ids.take(account.session.getBatch)
            account.connection
                .call(GetMethod(EmailType, account.id, JmapIds.Of(oldest), PROPERTIES))
                .list
                .any { it.isMuted }
        } ?: cachedMuted(message)

    /** The thread's Email ids, or the row's own emails when the server cannot be read. */
    suspend fun emailIds(message: MailMessage): List<String> =
        fromServer(message) { _, ids -> ids } ?: message.memberIds

    /** [read] with the thread's Email ids; null when the server could not be read or knows none. */
    private suspend fun <T> fromServer(
        message: MailMessage,
        read: suspend (MailAccount, List<String>) -> T,
    ): T? {
        if (message.emailId.isEmpty()) return null
        return jmapCallOr(null) {
            connections.mailAccount(message.location.accountId)?.let { account ->
                account.threadEmailIds(message.emailId).ifEmpty { null }?.let { read(account, it) }
            }
        }
    }

    /** Offline, the list's copies of the row's emails answer. */
    private suspend fun cachedMuted(message: MailMessage): Boolean =
        listOf(CacheKind.Email, CacheKind.Member).any { kind ->
            cache
                .objects(message.location.accountId, kind, EmailObject.serializer(), message.memberIds)
                .values
                .any { it.isMuted }
        }

    private companion object {
        val PROPERTIES = listOf("id", "receivedAt", "keywords")
    }
}
