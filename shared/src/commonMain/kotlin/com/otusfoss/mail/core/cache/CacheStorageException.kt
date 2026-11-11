package com.otusfoss.mail.core.cache

/**
 * The device could not read or persist cached mail. Cancellation and code errors are not storage
 * failures. A [transient] failure, such as a lock another connection holds, fails only this
 * operation and leaves the disk cache in use.
 */
class CacheStorageException(
    cause: Throwable,
    val transient: Boolean = false,
) : Exception("Mail cache is unavailable", cause)

internal expect fun <T> cacheStorage(block: () -> T): T
