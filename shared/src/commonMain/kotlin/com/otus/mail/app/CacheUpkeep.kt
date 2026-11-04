package com.otus.mail.app

import com.otus.mail.core.cache.CacheKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Drops cached bodies of mail received more than [BODY_RETENTION] before [now], so the cache does
 * not grow without bound. Call it once at start; it runs off the main thread.
 */
suspend fun MailServices.trimCache(now: Instant = Clock.System.now()) {
    cache.trim(CacheKind.Body, (now - BODY_RETENTION).toEpochMilliseconds())
}

/** How long a cached body stays after its mail arrived. Opening older mail fetches it again. */
private val BODY_RETENTION = 90.days
