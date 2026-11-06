package com.otus.mail.feature.folders.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.QueryMethod
import com.otus.jmap.jmapCallOr
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.core.mail.movePatch
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.feature.folders.domain.FolderRule
import kotlinx.serialization.json.JsonPrimitive

/**
 * Mail already in the Inbox and Archive that a new rule matches. Sieve only sees mail as it
 * arrives, so "Move the N that are already here" asks `Email/query` with the rule as a filter and
 * moves the matches through [changes], in batches within the server's `maxObjectsInSet`, so the
 * cached lists show the moves at once. The Inbox and Archive come from the server's folders now.
 */
internal class ExistingMail(
    private val connections: JmapConnections,
    private val mailboxes: Mailboxes,
    private val changes: EmailChangeQueue,
) {
    /** How many messages match; null when JMAP cannot filter by the rule or the server cannot be read. */
    suspend fun count(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int? =
        withAccount(accountId) { account ->
            val sources = sources(account, folderId)
            val filter = emailFilter(rule, sources) ?: return@withAccount null
            if (sources.isEmpty()) return@withAccount 0
            account.connection
                .call(QueryMethod(EmailFoldersType, account.id, filter, limit = 1, extra = CALCULATE_TOTAL))
                .total
                ?.toInt()
        }

    /** Moves the matches into [folderId] and returns how many moved; null on failure. */
    suspend fun move(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int? =
        withAccount(accountId) { account ->
            val sources = sources(account, folderId)
            if (sources.any { emailFilter(rule, listOf(it)) == null }) return@withAccount null
            sources.sumOf { moveFrom(account, it, folderId, rule) }
        }

    /**
     * Moves batch after batch out of [source]. Moved mail leaves the query's results, so each
     * query starts at the top. A move the server cannot take yet waits in [changes] and comes back
     * in the next query, so the loop ends once a batch moves nothing new.
     */
    private suspend fun moveFrom(
        account: MailAccount,
        source: String,
        target: String,
        rule: FolderRule,
    ): Int {
        val filter = emailFilter(rule, listOf(source)) ?: return 0
        val patch = movePatch(listOf(source), target)
        var moved = emptySet<String>()
        var rounds = 0
        do {
            val ids =
                account.connection
                    .call(QueryMethod(EmailFoldersType, account.id, filter, limit = account.session.setBatch))
                    .ids
            val taken = if (ids.isEmpty()) emptySet() else changes.update(account.login.id, ids.associateWith { patch })
            val added = taken - moved
            moved = moved + taken
            rounds++
        } while (added.isNotEmpty() && rounds < MAX_ROUNDS)
        return moved.size
    }

    /** The account's Inbox and Archive, without the target folder itself. */
    private suspend fun sources(
        account: MailAccount,
        folderId: String,
    ): List<String> =
        mailboxes
            .sync(account)
            .filter { it.role in SOURCE_ROLES && it.id != folderId }
            .map { it.id }

    /** [use]'s result, or null when the server or the saved login fails: the offer to move just goes. */
    private suspend fun <T> withAccount(
        accountId: String,
        use: suspend (MailAccount) -> T?,
    ): T? = jmapCallOr(null) { connections.mailAccount(accountId)?.let { use(it) } }

    private companion object {
        /** At most this many batches, so a server that keeps answering with the same mail cannot loop forever. */
        const val MAX_ROUNDS = 100
        val SOURCE_ROLES = setOf(MailboxRole.Inbox, MailboxRole.Archive)
        val CALCULATE_TOTAL = mapOf("calculateTotal" to JsonPrimitive(true))
    }
}
