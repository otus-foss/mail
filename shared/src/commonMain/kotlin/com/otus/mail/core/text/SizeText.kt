package com.otus.mail.core.text

import kotlin.math.round

/** Whole units in binary steps, as in "10 MB"; one decimal when needed, as in "1.5 MB". */
fun sizeText(bytes: Long): String {
    val (amount, unit) =
        SIZE_UNITS
            .lastOrNull { (step, _) -> bytes >= step }
            ?.let { (step, unit) -> bytes.toDouble() / step to unit }
            ?: (bytes.toDouble() to "bytes")
    val rounded = round(amount * DECIMAL) / DECIMAL
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$number $unit"
}

private const val KILOBYTE = 1024L
private const val DECIMAL = 10.0
private val SIZE_UNITS = listOf(KILOBYTE to "KB", KILOBYTE * KILOBYTE to "MB", KILOBYTE * KILOBYTE * KILOBYTE to "GB")
