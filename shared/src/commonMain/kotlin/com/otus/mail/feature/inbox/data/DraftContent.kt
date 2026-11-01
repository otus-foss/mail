package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.Draft

/** What the user wrote, then the signature after a "-- " line, then the quote, each after a blank line. */
internal fun Draft.textContent(): String =
    listOf(
        body,
        signature.text
            .takeIf(String::isNotBlank)
            ?.let { "$SIGNATURE_MARK\n$it" }
            .orEmpty(),
        quote,
    ).filter(String::isNotBlank)
        .joinToString("\n\n")

/**
 * The formatted body, the signature, and the quote with its quoted lines in a blockquote; null
 * while neither the body nor the signature has formatting, so the message stays plain text.
 */
internal fun Draft.htmlContent(): String? {
    if (html.isBlank() && signature.html.isBlank()) return null
    val formatted = html.ifBlank { body.escapeHtml().replace("\n", "<br>") }
    val signed = if (signature.html.isBlank()) "" else "<br><div>${signature.html}</div>"
    return formatted + signed + if (quote.isBlank()) "" else quote.quoteHtml()
}

private fun String.quoteHtml(): String =
    lines()
        .chunkedBy { it.startsWith(QUOTE_MARK) }
        .joinToString("", prefix = "<br>") { (quoted, lines) ->
            if (quoted) {
                lines.joinToString(
                    "<br>",
                    "<blockquote>",
                    "</blockquote>",
                ) { it.removePrefix(QUOTE_MARK).trimStart().escapeHtml() }
            } else {
                lines.joinToString("<br>", postfix = "<br>") { it.escapeHtml() }
            }
        }

/** Runs of neighboring lines that agree on [key], in order, found in one pass. */
private fun List<String>.chunkedBy(key: (String) -> Boolean): List<Pair<Boolean, List<String>>> {
    val keys = map(key)
    val starts = indices.filter { it == 0 || keys[it] != keys[it - 1] }
    return starts.mapIndexed { run, start -> keys[start] to subList(start, starts.getOrElse(run + 1) { size }) }
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private const val QUOTE_MARK = ">"

/** The line RFC 3676 puts before a signature, so readers can tell it apart. */
private const val SIGNATURE_MARK = "-- "
