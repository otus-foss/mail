package com.otus.mail.core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the cache for the life of the process, for previews, tests, and platforms without a database. */
class InMemoryMailCache : MailCache {
    private data class Entry(
        val json: String,
        val receivedMillis: Long?,
    )

    private data class Login(
        val objects: Map<CacheKind, Map<String, Entry>> = emptyMap(),
        val values: Map<String, String> = emptyMap(),
    )

    private val lock = Mutex()
    private var logins = emptyMap<String, Login>()

    override suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String> =
        lock.withLock {
            val objects = logins[loginId]?.objects?.get(kind).orEmpty()
            ids.mapNotNull { id -> objects[id]?.let { id to it.json } }.toMap()
        }

    override suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String> =
        lock.withLock {
            logins[loginId]
                ?.objects
                ?.get(kind)
                .orEmpty()
                .mapValues { it.value.json }
        }

    override suspend fun value(
        loginId: String,
        key: String,
    ): String? = lock.withLock { logins[loginId]?.values?.get(key) }

    override suspend fun write(
        loginId: String,
        changes: CacheWrite.() -> Unit,
    ) {
        lock.withLock {
            logins = logins + (loginId to Batch(logins[loginId] ?: Login()).apply(changes).login)
        }
    }

    override suspend fun trim(
        kind: CacheKind,
        epochMillis: Long,
    ) {
        lock.withLock {
            logins =
                logins.mapValues { (_, login) ->
                    val kept =
                        login.objects[kind].orEmpty().filterValues { (it.receivedMillis ?: epochMillis) >= epochMillis }
                    login.copy(objects = login.objects + (kind to kept))
                }
        }
    }

    override suspend fun forget(loginId: String) {
        lock.withLock { logins = logins - loginId }
    }

    private class Batch(
        var login: Login,
    ) : CacheWrite {
        override fun put(
            kind: CacheKind,
            id: String,
            json: String,
            receivedMillis: Long?,
        ) {
            update(kind) { it + (id to Entry(json, receivedMillis)) }
        }

        override fun remove(
            kind: CacheKind,
            ids: Collection<String>,
        ) {
            update(kind) { it - ids.toSet() }
        }

        override fun clear(kind: CacheKind) {
            update(kind) { emptyMap() }
        }

        override fun putValue(
            key: String,
            value: String?,
        ) {
            login = login.copy(values = if (value == null) login.values - key else login.values + (key to value))
        }

        private fun update(
            kind: CacheKind,
            change: (Map<String, Entry>) -> Map<String, Entry>,
        ) {
            login = login.copy(objects = login.objects + (kind to change(login.objects[kind].orEmpty())))
        }
    }
}
