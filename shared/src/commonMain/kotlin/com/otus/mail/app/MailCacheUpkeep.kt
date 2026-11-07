package com.otus.mail.app

import com.otus.mail.core.cache.CacheKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Drops cached bodies and Inbox categories of mail received more than [BODY_RETENTION] before
 * [now], and downloaded attachments not kept offline and unused for [ATTACHMENT_RETENTION], so
 * none grows without bound. Older mail is sorted again when a list shows it. Call it once at
 * start; it runs off the main thread. See docs/storage.md.
 */
suspend fun MailServices.trimCache(now: Instant = Clock.System.now()) {
    val oldMail = (now - BODY_RETENTION).toEpochMilliseconds()
    cache.trim(CacheKind.Body, oldMail)
    cache.trim(CacheKind.Category, oldMail)
    retention.trim((now - ATTACHMENT_RETENTION).toEpochMilliseconds())
}

/** How long a cached body stays after its mail arrived. Opening older mail fetches it again. */
private val BODY_RETENTION = 90.days

/** How long a downloaded attachment that is not kept offline stays after it was last used. */
private val ATTACHMENT_RETENTION = 7.days
