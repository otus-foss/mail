package com.otus.mail.feature.inbox.data

import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.withAccount
import com.otus.mail.core.categories.domain.CategoryVerdict
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.objects
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailSource

/**
 * [source]'s messages, each row with the Inbox category [records] gives its email, read from the
 * same [cache] the source lists from. While categories are off it reads nothing. A row's sender
 * counts as verified when the source found a DMARC pass for it, as a brand logo needs. Counting
 * and everything else pass through unchanged.
 */
class CategorizedMailSource(
    private val source: MailSource,
    private val cache: MailCache,
    private val records: CategoryRecords,
) : MailSource by source {
    override suspend fun messages(scope: MailScope): InboxSnapshot = source.messages(scope).sorted()

    override suspend fun messages(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = source.messages(scope, query).sorted()

    override suspend fun cached(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = source.cached(scope, query).sorted()

    override suspend fun more(
        scope: MailScope,
        query: MailQuery,
    ): InboxSnapshot = source.more(scope, query).sorted()

    private suspend fun InboxSnapshot.sorted(): InboxSnapshot {
        if (!records.active) return this
        val verdicts =
            messages
                .filter { it.emailId.isNotEmpty() }
                .groupBy { it.location.accountId }
                .flatMap { (loginId, rows) -> verdictsOf(loginId, rows).entries }
                .associate { it.key to it.value }
        return copy(messages = messages.map { row -> verdicts[row.id]?.let { row.copy(category = it) } ?: row })
    }

    /** The verdict of each of [rows] by [MailMessage.id], from their cached emails. */
    private suspend fun verdictsOf(
        loginId: String,
        rows: List<MailMessage>,
    ): Map<String, CategoryVerdict> =
        cache.withAccount(loginId) { account ->
            val emails = account.objects(loginId, CacheKind.Email, EmailObject.serializer(), rows.map { it.emailId })
            val verified = rows.filter { it.sender.brandDomain != null }.map { it.emailId }.toSet()
            val verdicts = records.verdicts(account, loginId, emails.values, verified)
            rows.mapNotNull { row -> verdicts[row.emailId]?.let { row.id to it } }.toMap()
        }
}
