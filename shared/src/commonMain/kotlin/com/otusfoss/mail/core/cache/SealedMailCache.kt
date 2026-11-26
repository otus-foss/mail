package com.otusfoss.mail.core.cache

import com.otusfoss.core.storage.TextCipher

/**
 * Seals every object and value with [cipher] before [cache] keeps it, so the mail in the device's
 * database cannot be read from its file. Ids, kinds, and received times stay as they are, so the
 * database still finds and trims rows. Each text is tied to its login, kind, and id, so a row
 * copied over another does not open. A row that does not open, as after the device's key was
 * lost, reads as missing, and sync fetches it again.
 */
class SealedMailCache(
    private val cache: MailCache,
    private val cipher: TextCipher,
) : MailCache {
    override suspend fun read(
        loginId: String,
        kind: CacheKind,
        ids: Collection<String>,
    ): Map<String, String> = opened(loginId, kind, cache.read(loginId, kind, ids))

    override suspend fun readAll(
        loginId: String,
        kind: CacheKind,
    ): Map<String, String> = opened(loginId, kind, cache.readAll(loginId, kind))

    override suspend fun value(
        loginId: String,
        key: String,
    ): String? = cache.value(loginId, key)?.let { cipher.open(it, valueContext(loginId, key)) }

    override suspend fun write(
        loginId: String,
        changes: CacheWrite.() -> Unit,
    ) = cache.write(loginId) { SealedWrite(this, loginId).changes() }

    override suspend fun trim(
        kind: CacheKind,
        epochMillis: Long,
    ) = cache.trim(kind, epochMillis)

    override suspend fun forget(loginId: String) = cache.forget(loginId)

    private fun opened(
        loginId: String,
        kind: CacheKind,
        sealed: Map<String, String>,
    ): Map<String, String> =
        sealed
            .mapNotNull { (id, json) -> cipher.open(json, objectContext(loginId, kind, id))?.let { id to it } }
            .toMap()

    private inner class SealedWrite(
        private val write: CacheWrite,
        private val loginId: String,
    ) : CacheWrite {
        override fun put(
            kind: CacheKind,
            id: String,
            json: String,
            receivedMillis: Long?,
        ) = write.put(kind, id, cipher.seal(json, objectContext(loginId, kind, id)), receivedMillis)

        override fun remove(
            kind: CacheKind,
            ids: Collection<String>,
        ) = write.remove(kind, ids)

        override fun clear(kind: CacheKind) = write.clear(kind)

        override fun putValue(
            key: String,
            value: String?,
        ) = write.putValue(key, value?.let { cipher.seal(it, valueContext(loginId, key)) })
    }
}

private fun objectContext(
    loginId: String,
    kind: CacheKind,
    id: String,
) = "object/${loginId.length}:$loginId/${kind.name}/$id"

private fun valueContext(
    loginId: String,
    key: String,
) = "value/${loginId.length}:$loginId/$key"
