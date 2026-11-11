package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.QueryMethod
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.allOf
import com.otusfoss.core.jmap.anyOf
import com.otusfoss.core.jmap.jmapCallOr
import com.otusfoss.mail.core.mail.EmailType
import com.otusfoss.mail.core.mail.MailAccount
import com.otusfoss.mail.core.mail.inMailbox
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draft copies a save may have stored though its answer was lost, by login, under the Message-ID
 * that save gave them. A later save of that login finds them in Drafts by that header (RFC 8621,
 * section 4.4.1) and destroys them, so a lost answer leaves no copy behind for good. At most
 * [MAX_KEPT] per login are remembered, for as long as the process runs. Saving and sending share
 * one, since a send is often the next thing after a save.
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
        val filter = allOf(inMailbox(drafts), anyOf(messageIds.map(::messageIdFilter)))
        jmapCallOr(Unit) {
            val found = account.connection.call(QueryMethod(EmailType, account.id, filter)).ids - kept
            if (found.isNotEmpty()) account.connection.call(SetMethod(EmailType, account.id, destroy = found))
            lock.withLock { lost[account.login.id] = lost[account.login.id].orEmpty() - messageIds.toSet() }
        }
    }

    private companion object {
        const val MAX_KEPT = 20
    }
}
