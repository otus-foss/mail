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

/**
 * What the user wrote in a saved draft, the signature it was saved with, and the quote of the
 * message it answers or forwards.
 */
data class SavedText(
    val body: String,
    val quote: String = "",
    val signature: String = "",
)

/**
 * Splits a saved draft's plain text, which Write stores as the body, then a signature after a
 * "-- " line, then the quote, each after a blank line (see `Draft.textContent`). Write adds the
 * identity's signature again, so the old one only tells whether the draft had one.
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
        signature =
            signature
                ?.let { lines.subList(it + 1, quote ?: lines.size).joinToString("\n").trim() }
                .orEmpty(),
    )
}

/**
 * The formatted body alone from a saved draft's HTML: what comes before the signature or the quote,
 * which Write marks with [SIGNATURE_CLASS] and [QUOTE_CLASS] (see `Draft.htmlContent`). Null when
 * the HTML has neither mark, as from another app, so none of it can be told from the body.
 */
fun String.savedHtmlBody(): String? =
    listOf(SIGNATURE_OPEN, QUOTE_OPEN)
        .mapNotNull { mark -> indexOf(mark).takeIf { it >= 0 } }
        .minOrNull()
        ?.let(::take)

/** The signature's HTML in a saved draft's HTML, without the `<div>` Write wraps it in; empty without one. */
fun String.savedHtmlSignature(): String =
    substringAfter(SIGNATURE_OPEN, "")
        .substringBefore(QUOTE_OPEN)
        .removeSuffix("</div>")

/** Whether line [this] of [lines] opens a reply's quote or a forward, after a blank line. */
private fun Int.startsQuote(lines: List<String>): Boolean =
    lines.getOrNull(this - 1).isNullOrBlank() &&
        (QUOTE_INTRO.matches(lines[this]) || lines[this] == FORWARD_HEADER)

/** The line RFC 3676 puts before a signature, so readers can tell it apart. */
const val SIGNATURE_MARK = "-- "

/** The class of the signature's `<div>` in the HTML Write stores, which the user's text never has. */
const val SIGNATURE_CLASS = "otus-signature"

/** The class of the quote's `<div>` in the HTML Write stores. */
const val QUOTE_CLASS = "otus-quote"

private const val SIGNATURE_OPEN = "<br><div class=\"$SIGNATURE_CLASS\">"

private const val QUOTE_OPEN = "<div class=\"$QUOTE_CLASS\">"
