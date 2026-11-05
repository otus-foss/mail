package com.otus.mail.feature.inbox.data

import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.objects
import com.otus.mail.core.mail.putObject
import com.otus.mail.feature.inbox.domain.category.CategoryClassifier
import com.otus.mail.feature.inbox.domain.category.CategoryVerdict
import com.otus.mail.feature.inbox.domain.category.SenderDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Sorts listed emails into the Inbox's categories on the device and keeps each verdict in the
 * cache beside the email, as [CacheKind.Category]. Each list read from the cache, which every sync
 * ends with, takes the saved verdicts of its rows and classifies the rest, at most [MAX_PER_READ]
 * at a time on [work], from what the device already holds: the row's headers and preview, and the
 * body when the message was opened. It downloads nothing. A verdict of an older classifier or sender list counts as missing, so a new
 * version classifies again. While [enabled] is false nothing new is classified. The [senders] list
 * loads once, on the first classification.
 */
class CategoryRecords(
    private val senders: suspend () -> SenderDirectory,
    private val enabled: () -> Boolean,
    private val work: CoroutineContext = Dispatchers.Default,
) {
    private val loading = Mutex()
    private var loaded: CategoryClassifier? = null

    /** [pages] with each row's Inbox category, from [verdicts]. */
    internal suspend fun sorted(
        cache: MailCache,
        loginId: String,
        pages: List<ListingPage<ListedRow>>,
    ): List<ListingPage<ListedRow>> {
        val emails = pages.flatMap { page -> page.rows.mapNotNull { it.email } }.distinctBy { it.id }
        val verdicts = verdicts(cache, loginId, emails)
        if (verdicts.isEmpty()) return pages
        return pages.map { page -> ListingPage(page.rows.map { it.sortedBy(verdicts) }, page.hasMore) }
    }

    /** The verdict of each of [rows] by email id; rows past the batch limit get theirs on a later read. */
    internal suspend fun verdicts(
        cache: MailCache,
        loginId: String,
        rows: Collection<EmailObject>,
    ): Map<String, CategoryVerdict> {
        if (!enabled() || rows.isEmpty()) return emptyMap()
        val classifier = classifier()
        val saved =
            cache
                .objects(loginId, CacheKind.Category, StoredCategory.serializer(), rows.map { it.id })
                .mapNotNull { (id, stored) -> stored.verdict(classifier.version)?.let { id to it } }
                .toMap()
        val missing = rows.filter { it.id !in saved }.take(MAX_PER_READ)
        return saved + classified(cache, loginId, missing, classifier)
    }

    /** [emails] classified from the device's copy, and saved for the next read. */
    private suspend fun classified(
        cache: MailCache,
        loginId: String,
        emails: List<EmailObject>,
        classifier: CategoryClassifier,
    ): Map<String, CategoryVerdict> {
        if (emails.isEmpty()) return emptyMap()
        val bodies = cache.objects(loginId, CacheKind.Body, EmailObject.serializer(), emails.map { it.id })
        val fresh =
            withContext(work) {
                emails.associate { it.id to classifier.classify(it.categorySignals(bodies[it.id])) }
            }
        save(cache, loginId, emails.associate { it.id to it.receivedMillis }, fresh)
        return fresh
    }

    private suspend fun save(
        cache: MailCache,
        loginId: String,
        received: Map<String, Long?>,
        verdicts: Map<String, CategoryVerdict>,
    ) {
        try {
            cache.write(loginId) {
                verdicts.forEach { (id, verdict) ->
                    val record = StoredCategory.of(verdict)
                    putObject(CacheKind.Category, id, StoredCategory.serializer(), record, received[id])
                }
            }
        } catch (expected: CacheStorageException) {
            // The verdicts still show; the next read classifies these emails again.
        }
    }

    private suspend fun classifier(): CategoryClassifier =
        loading.withLock {
            val current = loaded ?: CategoryClassifier(senders())
            loaded = current
            current
        }

    private companion object {
        /** How many emails one list read classifies, so a first read of a large cache stays short. */
        const val MAX_PER_READ = 300
    }
}

private fun ListedRow.sortedBy(verdicts: Map<String, CategoryVerdict>): ListedRow {
    val verdict = email?.let { verdicts[it.id] } ?: return this
    return ListedRow(message.copy(category = verdict), receivedAt, email)
}
