package com.otus.mail.core.avatar

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coalesces fetches while serializing persistent IO and invalidation separately from metadata. */
internal class AvatarLookupCache(
    private val store: AvatarStore,
    private val capacity: Int,
    private val lifetime: Long,
    private val now: () -> Long,
) {
    private val lock = Mutex()
    private val storageLock = Mutex()
    private val entries = linkedMapOf<AvatarCacheKey, StoredAvatar>()
    private val pending = mutableMapOf<AvatarCacheKey, Lookup>()
    private var generation = 0L

    suspend fun begin(): Long = lock.withLock { generation }

    suspend fun peek(key: AvatarCacheKey): StoredAvatar? = storageLock.withLock { peekStored(key) }

    suspend fun load(
        key: AvatarCacheKey,
        started: Long,
        fetch: suspend () -> AvatarImage?,
    ): StoredAvatar {
        while (true) {
            var owner = false
            val lookup =
                lock.withLock {
                    entries
                        .remove(key)
                        ?.also { entries[key] = it }
                        ?.takeIf(::fresh)
                        ?.let { return it }
                    if (started != generation) {
                        owner = true
                        Lookup(started)
                    } else {
                        pending.getOrPut(key) {
                            owner = true
                            Lookup(started)
                        }
                    }
                }
            val result = if (owner) fetchShared(key, lookup, fetch) else lookup.result.await()
            if (result != null) return result
        }
    }

    suspend fun clear(account: String? = null) {
        storageLock.withLock {
            lock.withLock {
                generation++
                if (account == null) {
                    entries.clear()
                    pending.clear()
                } else {
                    entries.keys.removeAll { it.account == account }
                    pending.keys.removeAll { it.account == account }
                }
            }
            if (account == null) store.clear() else store.removeAccount(account)
        }
    }

    private suspend fun fetchShared(
        key: AvatarCacheKey,
        lookup: Lookup,
        fetch: suspend () -> AvatarImage?,
    ): StoredAvatar {
        var result: StoredAvatar? = null
        try {
            val saved =
                storageLock.withLock {
                    if (lock.withLock { generation == lookup.generation }) peekStored(key) else null
                }
            if (saved != null && fresh(saved)) return saved.also { result = it }
            val entry = StoredAvatar(fetch() ?: saved?.image, now())
            storageLock.withLock {
                val current =
                    lock.withLock {
                        (generation == lookup.generation).also { if (it) remember(key, entry) }
                    }
                if (current) store.write(key, entry)
            }
            return entry.also { result = it }
        } finally {
            withContext(NonCancellable) {
                lock.withLock { if (pending[key] === lookup) pending.remove(key) }
            }
            lookup.result.complete(result)
        }
    }

    /** Storage lock held; metadata lock never covers disk IO or network work. */
    private suspend fun peekStored(key: AvatarCacheKey): StoredAvatar? =
        lock.withLock { entries.remove(key)?.also { entries[key] = it } }
            ?: store.read(key)?.also { lock.withLock { remember(key, it) } }

    private fun fresh(entry: StoredAvatar): Boolean {
        val age = now() - entry.created
        val ttl = if (entry.image == null) minOf(lifetime, MISS_LIFETIME) else lifetime
        return age >= 0 && age < ttl
    }

    private fun remember(
        key: AvatarCacheKey,
        entry: StoredAvatar,
    ) {
        entries.remove(key)
        entries[key] = entry
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    private class Lookup(
        val generation: Long,
        val result: CompletableDeferred<StoredAvatar?> = CompletableDeferred(),
    )

    private companion object {
        const val MISS_LIFETIME = 5 * 60 * 1000L
    }
}
