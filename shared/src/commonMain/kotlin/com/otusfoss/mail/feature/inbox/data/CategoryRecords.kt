package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.categories.domain.CategoryVerdict
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.objects
import com.otusfoss.mail.core.mail.putObject
import com.otusfoss.mail.core.messages.data.receivedMillis
import com.otusfoss.mail.feature.inbox.domain.category.CategoryClassifier
import com.otusfoss.mail.feature.inbox.domain.category.SenderDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Sorts listed emails into the Inbox's categories on the device and keeps each verdict in the
 * cache beside the email, as [CacheKind.Category]. Each list read takes the saved verdicts of its
 * rows and classifies the rest on [work], from what the device already holds: the row's headers
 * and preview, and the body when the message was opened. It downloads nothing. A verdict of an
 * older classifier or sender list counts as missing, so a new version classifies again. While
 * [enabled] is false nothing new is classified. The [senders] list loads once, on the first
 * classification.
 *
 * A verdict stays as it was first listed while Mail runs, so opening a message never moves it to
 * another pill under the user. Opening drops the saved verdict, so the body's markup counts from
 * the next start.
 */
class CategoryRecords(
    private val senders: suspend () -> SenderDirectory,
    private val enabled: () -> Boolean,
    private val work: CoroutineContext = Dispatchers.Default,
) {
    private val lock = Mutex()
    private var loaded: CategoryClassifier? = null
    private var listed: Map<String, CategoryVerdict> = emptyMap()

    /** Whether the user has categories on. */
    val active: Boolean
        get() = enabled()

    /** The verdict of each of [rows] by email id; [verified] holds the ids whose sender passed DMARC. */
    internal suspend fun verdicts(
        cache: MailCache,
        loginId: String,
        rows: Collection<EmailObject>,
        verified: Set<String> = emptySet(),
    ): Map<String, CategoryVerdict> {
        if (!active || rows.isEmpty()) return emptyMap()
        val classifier = classifier()
        val shown =
            lock.withLock { rows.mapNotNull { row -> listed[key(loginId, row.id)]?.let { row.id to it } }.toMap() }
        val unseen = rows.filter { it.id !in shown }
        val saved =
            cache
                .objects(loginId, CacheKind.Category, StoredCategory.serializer(), unseen.map { it.id })
                .mapNotNull { (id, stored) -> stored.verdict(classifier.version)?.let { id to it } }
                .toMap()
        val fresh = classified(cache, loginId, unseen.filter { it.id !in saved }, verified, classifier)
        val found = saved + fresh
        lock.withLock { listed = listed + found.mapKeys { (id, _) -> key(loginId, id) } }
        return shown + found
    }

    /** [emails] classified from the device's copy, and saved for the next read. */
    private suspend fun classified(
        cache: MailCache,
        loginId: String,
        emails: List<EmailObject>,
        verified: Set<String>,
        classifier: CategoryClassifier,
    ): Map<String, CategoryVerdict> {
        if (emails.isEmpty()) return emptyMap()
        val bodies = cache.objects(loginId, CacheKind.Body, EmailObject.serializer(), emails.map { it.id })
        val fresh =
            withContext(work) {
                emails.associate { email ->
                    email.id to classifier.classify(email.categorySignals(bodies[email.id], email.id in verified))
                }
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
            // The verdicts still show; the next start classifies these emails again.
        }
    }

    private suspend fun classifier(): CategoryClassifier =
        lock.withLock {
            val current = loaded ?: CategoryClassifier(senders())
            loaded = current
            current
        }

    private fun key(
        loginId: String,
        emailId: String,
    ) = "$loginId\n$emailId"
}
