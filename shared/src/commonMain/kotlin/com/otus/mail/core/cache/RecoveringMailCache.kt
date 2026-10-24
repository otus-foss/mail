package com.otus.mail.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Continues with an empty memory cache after a disk failure. Server sync repopulates it; the
 * broken database is not retried until process restart. Account removal still must erase disk data.
 */
internal class RecoveringMailCache(
    private val disk: MailCache,
) : MailCache {
    private val lock = Mutex()
    private var active = disk

    override suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String> = access { read(loginId, kind, ids) }

    override suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String> = access { readAll(loginId, kind) }

    override suspend fun value(
        loginId: String,
        key: String,
    ): String? = access { value(loginId, key) }

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

    private suspend fun <T> access(
        reportWriteFailure: Boolean = false,
        operation: suspend MailCache.() -> T,
    ): T =
        lock.withLock {
            try {
                active.operation()
            } catch (unavailable: CacheStorageException) {
                if (active !== disk) throw unavailable
                active = InMemoryMailCache()
                val result = active.operation()
                if (reportWriteFailure) throw unavailable
                result
            }
        }
}
