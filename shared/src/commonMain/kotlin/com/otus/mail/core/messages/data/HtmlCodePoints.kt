package com.otus.mail.core.messages.data

/**
 * The character a code point names, as in browsers: U+FFFD for zero, a surrogate, or a value beyond
 * Unicode, and the Windows-1252 character for 128 to 159, so `&#146;` is an apostrophe.
 */
internal fun codePointText(code: Int): String =
    when {
        code == 0 || code > MAX_CODE_POINT || code in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code -> "\uFFFD"
        code in WINDOWS_1252_START until WINDOWS_1252_START + WINDOWS_1252.length -> windowsCharacter(code)
        code < SUPPLEMENTARY -> Char(code).toString()
        else -> supplementaryCharacter(code)
    }

private fun windowsCharacter(code: Int): String = WINDOWS_1252[code - WINDOWS_1252_START].toString()

private fun supplementaryCharacter(code: Int): String {
    val offset = code - SUPPLEMENTARY
    val high = Char.MIN_HIGH_SURROGATE + (offset shr SURROGATE_BITS)
    val low = Char.MIN_LOW_SURROGATE + (offset and SURROGATE_MASK)
    return charArrayOf(high, low).concatToString()
}

private const val MAX_CODE_POINT = 0x10FFFF
private const val SUPPLEMENTARY = 0x10000
private const val SURROGATE_BITS = 10
private const val SURROGATE_MASK = 0x3FF
private const val WINDOWS_1252_START = 0x80

/** What browsers read numeric references 128 to 159 as; the five unassigned ones stay control characters. */
private const val WINDOWS_1252 =
    "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
        "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"
