package com.otus.mail.core.mail

import kotlin.time.Instant

/** A `UTCDate` (RFC 8620, section 1.4): whole seconds and a `Z`. */
internal fun Instant.utcDate(): String = Instant.fromEpochSeconds(epochSeconds).toString()
