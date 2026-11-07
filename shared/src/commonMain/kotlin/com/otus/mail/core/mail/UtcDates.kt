package com.otus.mail.core.mail

import com.otus.json.types.toUtcDate
import com.otus.json.types.toUtcDateOrNull
import kotlin.time.Instant

/** A `UTCDate` (RFC 8620, section 1.4) in whole seconds, as Mail sends it in filters and FUTURERELEASE. */
internal fun Instant.utcDate(): String = Instant.fromEpochSeconds(epochSeconds).toUtcDate()

/**
 * When this email arrived, the one reading of its `receivedAt` (RFC 8621, section 4.1.2.1) every
 * feature uses; null when the server sent something that is not a date.
 */
internal val EmailObject.received: Instant?
    get() = receivedAt.toUtcDateOrNull()

/**
 * [received] for a load that cannot go on without it. Throws [IllegalArgumentException], which
 * [com.otus.jmap.jmapDecoding] reads as data the server got wrong.
 */
internal val EmailObject.receivedTime: Instant
    get() = received ?: throw IllegalArgumentException("receivedAt is not a date")
