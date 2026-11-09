package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.Draft

/** Loads a draft's saved copy whole, to edit again in Write or to send as it is. */
fun interface SavedDrafts {
    /**
     * The draft [emailId] of [accountId] holds, keeping that id, so a save or send replaces the
     * copy; null when it could not be loaded. Unless [whole], its signature is dropped and its quote
     * split off, as Write expects; a [whole] draft holds the copy's text and HTML as they are.
     */
    suspend fun load(
        accountId: String,
        emailId: String,
        whole: Boolean,
    ): Draft?
}

/** What the user wrote in a saved draft, and the quote of the message it answers or forwards. */
data class SavedText(
    val body: String,
    val quote: String = "",
)

/**
 * Splits a saved draft's plain text, which Write stores as the body, then a signature after a
 * "-- " line, then the quote, each after a blank line (see `Draft.textContent`). Write adds the
 * identity's signature again, so the old one is dropped.
 */
fun String.savedParts(): SavedText {
    val lines = lines()
    val signature = lines.indexOf(SIGNATURE_MARK).takeIf { it >= 0 }
    val quote =
        lines.indices
            .drop(signature ?: 0)
            .firstOrNull { it.startsQuote(lines) }
    val bodyEnd = listOfNotNull(signature, quote).minOrNull() ?: lines.size
    return SavedText(
        body = lines.take(bodyEnd).joinToString("\n").trimEnd(),
        quote = quote?.let { lines.drop(it).joinToString("\n").trimEnd() }.orEmpty(),
    )
}

/**
 * The formatted body alone from a saved draft's [html], when its text had a signature or a quote
 * after the body: Write stores those after a `<br>`, the signature in a `<div>` and the quote as a
 * blockquote or its header line (see `Draft.htmlContent`).
 */
fun String.savedHtmlBody(endsEarly: Boolean): String =
    if (!endsEarly) {
        this
    } else {
        HTML_TAILS
            .mapNotNull { tail -> indexOf(tail).takeIf { it >= 0 } }
            .minOrNull()
            ?.let(::take) ?: this
    }

/** Whether line [this] of [lines] opens a reply's quote or a forward, after a blank line. */
private fun Int.startsQuote(lines: List<String>): Boolean =
    lines.getOrNull(this - 1).isNullOrBlank() &&
        (QUOTE_INTRO.matches(lines[this]) || lines[this] == FORWARD_HEADER)

/** The line RFC 3676 puts before a signature, so readers can tell it apart. */
const val SIGNATURE_MARK = "-- "

private val HTML_TAILS = listOf("<br><div>", "<br><blockquote>", "<br>On ", "<br>$FORWARD_HEADER")
