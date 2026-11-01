package com.otus.mail.feature.inbox.data

import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.condition
import com.otus.mail.core.mail.operator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

/**
 * Draft copies a save may have stored though its answer was lost, by login, under the Message-ID
 * that save gave them. A later save of that login finds them in Drafts by that header (RFC 8621,
 * section 4.4.1) and destroys them, so a lost answer leaves no copy behind for good. At most
 * [MAX_KEPT] per login are remembered, for as long as the process runs.
 */
internal class UnansweredDrafts {
    private val lock = Mutex()
    private val lost = mutableMapOf<String, List<String>>()

    suspend fun remember(
        loginId: String,
        messageId: String,
    ) {
        lock.withLock { lost[loginId] = (lost[loginId].orEmpty() + messageId).takeLast(MAX_KEPT) }
    }

    /** Destroys the copies remembered for [account] that are in [drafts], but [kept], the copy just stored. */
    suspend fun destroy(
        account: MailAccount,
        drafts: String,
        kept: String,
    ) {
        val messageIds = lock.withLock { lost[account.login.id].orEmpty() }
        if (messageIds.isEmpty()) return
        val inDrafts = condition("inMailbox", JsonPrimitive(drafts))
        val filter = operator("AND", listOf(inDrafts, operator("OR", messageIds.map(::messageIdFilter))))
        bestEffort {
            val found = account.connection.call(QueryMethod(EmailType, account.id, filter)).ids - kept
            if (found.isNotEmpty()) account.connection.call(SetMethod(EmailType, account.id, destroy = found))
            lock.withLock { lost[account.login.id] = lost[account.login.id].orEmpty() - messageIds.toSet() }
        }
    }

    private companion object {
        const val MAX_KEPT = 20
    }
}
