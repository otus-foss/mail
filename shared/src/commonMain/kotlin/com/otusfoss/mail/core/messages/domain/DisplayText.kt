package com.otusfoss.mail.core.messages.domain

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

/**
 * An email address from a server, trimmed. A different address must never become a known one, so
 * nothing is removed: an address with a character [displayLine] would drop or change, or with a
 * backslash, shows each of them as an escape such as `\u{200E}`. That form is visible, unique to
 * the address, and fails [isUsableAddress].
 */
fun String.displayAddress(): String =
    trim().let { address ->
        if (address.none { it.isUnsafeInAddress() }) {
            address
        } else {
            address.map { if (it.isUnsafeInAddress()) it.escaped() else "$it" }.joinToString("")
        }
    }

/**
 * Whether this address can stand for a person, to match a contact, load an avatar, or name a rule.
 * False when it is blank or has anything [displayAddress] escapes.
 */
fun String.isUsableAddress(): Boolean = isNotBlank() && none { it.isUnsafeInAddress() }

private fun Char.isUnsafeInAddress(): Boolean = isHidden() || isWhitespace() || this == '\\'

private fun Char.escaped(): String = "\\u{${code.toString(HEX).uppercase().padStart(4, '0')}}"

private fun Char.isHidden(): Boolean = (isISOControl() && this != '\t' && this != '\n') || this in BIDI_CONTROLS

private val BIDI_CONTROLS =
    setOf('\u061C', '\u200E', '\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069')

private const val HEX = 16
