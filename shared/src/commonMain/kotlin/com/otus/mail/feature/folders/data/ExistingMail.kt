package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapException
import com.otus.jmap.QueryMethod
import com.otus.jmap.SetMethod
import com.otus.mail.core.mail.EmailFoldersType
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailAccounts
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.pointer
import com.otus.mail.feature.folders.domain.ARCHIVE_ROLE
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.FolderStorageException
import com.otus.mail.feature.folders.domain.INBOX_ROLE
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Mail already in the Inbox and Archive that a new rule matches. Sieve only sees mail as it
 * arrives, so "Move the N that are already here" asks `Email/query` with the rule as a filter and
 * moves the matches with `Email/set`, in batches within the server's `maxObjectsInSet`.
 */
internal class ExistingMail(
    private val accounts: MailAccounts,
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
            val query = QueryMethod(EmailFoldersType, account.id, filter, limit = 1, extra = CALCULATE_TOTAL)
            account.connection
                .call(query)
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
     * query starts at the top; a batch the server refuses entirely ends the loop.
     */
    private suspend fun moveFrom(
        account: MailAccount,
        source: String,
        target: String,
        rule: FolderRule,
    ): Int {
        val filter = emailFilter(rule, listOf(source)) ?: return 0
        val batch = (account.session.maxObjectsInSet ?: MAX_BATCH).coerceIn(1, MAX_BATCH).toInt()
        val patch =
            buildJsonObject {
                put("mailboxIds/${pointer(source)}", JsonNull)
                put("mailboxIds/${pointer(target)}", JsonPrimitive(true))
            }
        var moved = 0
        var rounds = 0
        var more = true
        while (more && rounds < MAX_ROUNDS) {
            val ids = account.connection.call(QueryMethod(EmailFoldersType, account.id, filter, limit = batch)).ids
            val updated =
                if (ids.isEmpty()) {
                    0
                } else {
                    account.connection
                        .call(SetMethod(EmailFoldersType, account.id, update = ids.associateWith { patch }))
                        .updated.size
                }
            moved += updated
            rounds++
            more = updated > 0
        }
        return moved
    }

    /** The account's Inbox and Archive, without the target folder itself. */
    private suspend fun sources(
        account: MailAccount,
        folderId: String,
    ): List<String> =
        account.connection
            .call(GetMethod(MailboxType, account.id, properties = listOf("id", "role")))
            .list
            .filter { it.role?.lowercase() in SOURCE_ROLES && it.id != folderId }
            .map { it.id }

    private suspend fun <T> withAccount(
        accountId: String,
        use: suspend (MailAccount) -> T?,
    ): T? =
        try {
            accounts.open(accountId)?.let { use(it) }
        } catch (expected: JmapException) {
            null
        } catch (expected: FolderStorageException) {
            null
        }

    private companion object {
        const val MAX_BATCH = 100L

        /** At most this many batches, so a server that keeps answering with the same mail cannot loop forever. */
        const val MAX_ROUNDS = 100
        val SOURCE_ROLES = setOf(INBOX_ROLE, ARCHIVE_ROLE)
        val CALCULATE_TOTAL = mapOf("calculateTotal" to JsonPrimitive(true))
    }
}
