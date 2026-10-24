package com.otus.mail.core.cache

/** The device could not read or persist cached mail. Cancellation and code errors are not storage failures. */
class CacheStorageException(
    cause: Throwable,
) : Exception("Mail cache is unavailable", cause)

internal expect fun <T> cacheStorage(block: () -> T): T
