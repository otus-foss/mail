package com.otus.mail.core.cache

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Keeps the cache in an SQLite database through [driver], so it outlives the process. Platform
 * entry points create the driver for [MailDatabase.Schema]. Every call runs on [Dispatchers.IO].
 */
class SqlMailCache(
    driver: SqlDriver,
) : MailCache {
    private val database = MailDatabase(driver)
    private val objects = database.cachedObjectQueries
    private val values = database.cachedValueQueries

    override suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String> =
        io {
            database.transactionWithResult {
                ids
                    .distinct()
                    .chunked(MAX_IDS_PER_QUERY)
                    .flatMap { chunk ->
                        objects.readByIds(loginId, kind.name, chunk) { id, json -> id to json }.executeAsList()
                    }.toMap()
            }
        }

    override suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String> =
        io {
            objects
                .readKind(loginId, kind.name) { id, json -> id to json }
                .executeAsList()
                .toMap()
        }

    override suspend fun value(
        loginId: String,
        key: String,
    ): String? = io { values.read(loginId, key).executeAsOneOrNull() }

    override suspend fun write(
        loginId: String,
        changes: CacheWrite.() -> Unit,
    ) {
        io { database.transaction { SqlWrite(loginId).changes() } }
    }

    override suspend fun trim(
        kind: CacheKind,
        epochMillis: Long,
    ) {
        io { objects.trim(kind.name, epochMillis) }
    }

    override suspend fun forget(loginId: String) {
        io {
            database.transaction {
                objects.forgetLogin(loginId)
                values.forgetLogin(loginId)
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { cacheStorage(block) }

    /** Runs each change at once, inside the transaction [write] opened. */
    private inner class SqlWrite(
        private val loginId: String,
    ) : CacheWrite {
        override fun put(
            kind: CacheKind,
            id: String,
            json: String,
            receivedMillis: Long?,
        ) {
            objects.upsert(loginId, kind.name, id, json, receivedMillis)
        }

        override fun remove(
            kind: CacheKind,
            ids: Collection<String>,
        ) {
            ids.distinct().chunked(MAX_IDS_PER_QUERY).forEach { objects.deleteByIds(loginId, kind.name, it) }
        }

        override fun clear(kind: CacheKind) {
            objects.deleteKind(loginId, kind.name)
        }

        override fun putValue(
            key: String,
            value: String?,
        ) {
            if (value == null) values.delete(loginId, key) else values.upsert(loginId, key, value)
        }
    }

    private companion object {
        /**
         * Ids bound in one `IN` list. Older Android releases ship SQLite builds that allow only 999
         * bound variables per statement, and the login and kind take two more.
         */
        const val MAX_IDS_PER_QUERY = 500
    }
}
