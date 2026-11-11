package com.otusfoss.mail.core.text

import kotlin.math.round

/** Whole units in binary steps, as in "10 MB"; one decimal when needed, as in "1.5 MB". */
fun sizeText(bytes: Long): String {
    val fits = SIZE_UNITS.indexOfLast { (step, _) -> bytes >= step }
    // Rounding can reach the next unit: 1,048,524 bytes read "1 MB", not "1024 KB".
    val index = if (fits in 0 until SIZE_UNITS.lastIndex && bytes.inUnit(fits) >= KILOBYTE) fits + 1 else fits
    val (amount, unit) = if (index < 0) bytes.toDouble() to "bytes" else bytes.inUnit(index) to SIZE_UNITS[index].second
    val number = if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
    return "$number $unit"
}

/** These bytes in the unit at [index] of [SIZE_UNITS], to one decimal. */
private fun Long.inUnit(index: Int): Double = round(toDouble() / SIZE_UNITS[index].first * DECIMAL) / DECIMAL

private const val KILOBYTE = 1024L
private const val DECIMAL = 10.0
private val SIZE_UNITS = listOf(KILOBYTE to "KB", KILOBYTE * KILOBYTE to "MB", KILOBYTE * KILOBYTE * KILOBYTE to "GB")
