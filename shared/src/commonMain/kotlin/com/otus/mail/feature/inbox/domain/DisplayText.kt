package com.otus.mail.feature.inbox.domain

/**
 * Untrusted text from a server, such as a sender name or subject, on one line. Removes hidden
 * characters ([displayText]) and turns each run of whitespace, line breaks included, into one space.
 */
fun String.displayLine(): String =
    displayText()
        .map { if (it.isWhitespace()) ' ' else it }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/**
 * Untrusted multiline text, such as a message body, with line breaks normalized to `\n`. Removes
 * control characters other than tab and newline, and the bidirectional marks, embeddings,
 * overrides, and isolates that can reorder a name like "PayPal" on screen.
 */
fun String.displayText(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .filterNot { it.isHidden() }

private fun Char.isHidden(): Boolean = (isISOControl() && this != '\t' && this != '\n') || this in BIDI_CONTROLS

private val BIDI_CONTROLS =
    setOf('\u061C', '\u200E', '\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069')
