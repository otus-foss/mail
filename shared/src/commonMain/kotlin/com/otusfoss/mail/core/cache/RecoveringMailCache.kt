package com.otusfoss.mail.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Continues with an empty memory cache after a permanent disk failure. Server sync repopulates it;
 * the broken database is not retried until process restart. A transient lock fails only its own
 * operation. Account removal still must erase disk data.
 */
internal class RecoveringMailCache(
    private val disk: MailCache,
) : MailCache {
    private val lock = Mutex()

    @Volatile private var active = disk
    private val fallback by lazy { InMemoryMailCache() }

    override suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String> = reading { read(loginId, kind, ids) }

    override suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String> = reading { readAll(loginId, kind) }

    override suspend fun value(
        loginId: String,
        key: String,
    ): String? = reading { value(loginId, key) }

    override suspend fun write(
        loginId: String,
        changes: CacheWrite.() -> Unit,
    ): Unit = access(reportWriteFailure = true) { write(loginId, changes) }

    override suspend fun trim(
        kind: CacheKind,
        epochMillis: Long,
    ): Unit = access { trim(kind, epochMillis) }

    override suspend fun forget(loginId: String) {
        lock.withLock {
            if (active !== disk) active.forget(loginId)
            disk.forget(loginId)
        }
    }

    /**
     * A read takes no turn behind the writes, so an opened message never waits for a list sync to
     * be saved; the storage itself keeps a read and a write apart.
     */
    private suspend fun <T> reading(operation: suspend MailCache.() -> T): T = recovering(false, operation)

    private suspend fun <T> access(
        reportWriteFailure: Boolean = false,
        operation: suspend MailCache.() -> T,
    ): T = lock.withLock { recovering(reportWriteFailure, operation) }

    private suspend fun <T> recovering(
        reportWriteFailure: Boolean,
        operation: suspend MailCache.() -> T,
    ): T {
        val used = active
        return try {
            used.operation()
        } catch (unavailable: CacheStorageException) {
            if (unavailable.transient || used !== disk) throw unavailable
            val result = memory().operation()
            if (reportWriteFailure) throw unavailable
            result
        }
    }

    /** Puts the memory cache in the broken disk's place; every caller that finds it broken gets the same one. */
    private fun memory(): MailCache = fallback.also { active = it }
}
