package com.otus.mail.feature.inbox.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapIds
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MUTED_KEYWORD
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.mailCallOr
import com.otus.mail.core.mail.objects
import com.otus.mail.feature.inbox.domain.ConversationMute
import com.otus.mail.feature.inbox.domain.MailMessage

/**
 * Mutes a conversation by setting [MUTED_KEYWORD] on every email of its thread with `Email/set`
 * (RFC 8621, sections 4.1.1 and 4.6), so another device sees it too. It reads the thread's ids
 * first, since the row lists only the emails in its folder. Muting marks every email, so reading
 * the keywords of the oldest ones that fit one `Email/get` tells whether it is muted. The emails it
 * changed change in [cache].
 */
class JmapConversationMute(
    private val connections: JmapConnections,
    private val logins: LoginStore,
    private val cache: MailCache = InMemoryMailCache(),
) : ConversationMute {
    override suspend fun isMuted(message: MailMessage): Boolean =
        fromServer(message) { account, ids ->
            val oldest = ids.take(account.session.coreLimit("maxObjectsInGet", GET_BATCH))
            account.connection
                .call(GetMethod(EmailType, account.id, JmapIds.Of(oldest), PROPERTIES))
                .list
                .any { it.keywords[MUTED_KEYWORD] == true }
        } ?: cachedMuted(message)

    override suspend fun setMuted(
        message: MailMessage,
        muted: Boolean,
    ): Boolean {
        return cache.withAccount(message.location.accountId) { accountCache ->
            val ids = fromServer(message) { _, ids -> ids } ?: return@withAccount false
            val patch = membershipPatch("keywords", MUTED_KEYWORD, muted)
            val updated =
                updateAccountEmails(
                    connections,
                    logins,
                    message.location.accountId,
                    ids.associateWith { patch },
                    accountCache,
                )
            updated.containsAll(ids)
        }
    }

    /** [read] with the thread's Email ids; null when the server could not be read or knows none. */
    private suspend fun <T> fromServer(
        message: MailMessage,
        read: suspend (MailAccount, List<String>) -> T,
    ): T? {
        if (message.emailId.isEmpty()) return null
        return mailCallOr(null) {
            mailAccount(connections, logins, message.location.accountId)?.let { account ->
                account.threadEmailIds(message.emailId).ifEmpty { null }?.let { read(account, it) }
            }
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

        /** Read when the server names no `maxObjectsInGet`; small enough for any server. */
        const val GET_BATCH = 200
    }
}
