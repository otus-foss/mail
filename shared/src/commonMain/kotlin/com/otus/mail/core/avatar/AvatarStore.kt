package com.otus.mail.core.avatar

import kotlin.time.Clock

/** Cache policy and storage can be replaced together for previews and tests. */
class AvatarCacheConfig(
    val maxEntries: Int = 64,
    val cacheMillis: Long = DEFAULT_CACHE_MILLIS,
    val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val store: AvatarStore = EmptyAvatarStore,
) {
    init {
        require(maxEntries > 0 && cacheMillis > 0)
    }

    private companion object {
        const val DEFAULT_CACHE_MILLIS = 30 * 24 * 60 * 60 * 1000L
    }
}

/** Public website icons have no account; contact photos remain private to one login. */
data class AvatarCacheKey(
    val account: String?,
    val value: String,
)

data class StoredAvatar(
    val image: AvatarImage?,
    val created: Long,
)

/** Optional encoded-image storage. Implementations bound disk usage and tolerate corrupt records. */
interface AvatarStore {
    suspend fun read(key: AvatarCacheKey): StoredAvatar?

    suspend fun write(
        key: AvatarCacheKey,
        entry: StoredAvatar,
    )

    suspend fun clear()

    suspend fun removeAccount(account: String)
}

internal object EmptyAvatarStore : AvatarStore {
    override suspend fun read(key: AvatarCacheKey): StoredAvatar? = null

    override suspend fun write(
        key: AvatarCacheKey,
        entry: StoredAvatar,
    ) = Unit

    override suspend fun clear() = Unit

    override suspend fun removeAccount(account: String) = Unit
}
