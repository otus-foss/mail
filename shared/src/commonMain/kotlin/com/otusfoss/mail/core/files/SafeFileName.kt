package com.otusfoss.mail.core.files

import com.otusfoss.mail.core.messages.domain.displayLine

/**
 * A file name the sender picked, safe to store, save, or hand to another app. Path separators
 * cannot leave the folder, and control and bidirectional characters are dropped, so
 * `photo<RLO>gpj.exe` cannot read as `photoexe.jpg`. Leading dots, which hide a file or mark the
 * download folder's own files, go too. A long name is cut to [MAX_NAME_BYTES] UTF-8 bytes before
 * its extension, so file systems with a 255-byte limit take it and its type survives.
 */
fun safeFileName(name: String): String =
    name
        .displayLine()
        .replace('/', '_')
        .replace('\\', '_')
        .trimStart('.', ' ')
        .ifEmpty { FALLBACK_NAME }
        .cappedAt(MAX_NAME_BYTES)

/** This name within [maxBytes] UTF-8 bytes, cutting the part before a short extension. */
private fun String.cappedAt(maxBytes: Int): String {
    if (utf8Size() <= maxBytes) return this
    val extension =
        substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.length < MAX_EXTENSION_LENGTH }
            ?.let { ".$it" }
            .orEmpty()
    return removeSuffix(extension).utf8Prefix(maxBytes - extension.utf8Size()).trimEnd() + extension
}

/** The longest start of this text within [maxBytes] UTF-8 bytes that does not split a character. */
private fun String.utf8Prefix(maxBytes: Int): String =
    generateSequence(take(maxBytes)) { it.dropLast(1) }
        .first { it.utf8Size() <= maxBytes }
        .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }

private fun String.utf8Size(): Int = encodeToByteArray().size

private const val FALLBACK_NAME = "attachment"

/** Leaves room for a time stamp or "(1)" that a save adds when the name is taken. */
private const val MAX_NAME_BYTES = 200
private const val MAX_EXTENSION_LENGTH = 16
