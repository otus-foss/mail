package com.otus.mail.feature.compose.domain

import com.otus.mail.core.messages.domain.Draft

/** Loads a draft's saved copy whole, to edit again in Write or to send as it is. */
fun interface SavedDrafts {
    /**
     * The draft [emailId] of [accountId] holds, keeping that id, so a save or send replaces the
     * copy; null when it could not be loaded. Unless [whole], its signature and quote are split off
     * the body, as Write expects; a [whole] draft holds the copy's text and HTML as they are.
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
    val quote = lines.indices.firstOrNull { it.startsQuote(lines) }
    val signature =
        lines
            .take(quote ?: lines.size)
            .indexOf(SIGNATURE_MARK)
            .takeIf { it >= 0 }
    val bodyEnd = signature ?: quote ?: lines.size
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
    substringBefore(QUOTE_OPEN)
        .substringAfter(SIGNATURE_OPEN, "")
        .removeSuffix("</div>")

/**
 * Whether line [this] of [lines] opens a reply's quote or a forward, after a blank line. Each opens
 * the way Write writes it, so a line the user wrote, such as "On Monday we wrote:", stays body.
 */
private fun Int.startsQuote(lines: List<String>): Boolean {
    val next = lines.getOrNull(this + 1).orEmpty()
    return lines.getOrNull(this - 1).isNullOrBlank() &&
        when {
            QUOTE_INTRO.matches(lines[this]) -> next.startsWith(QUOTE)
            lines[this] == FORWARD_HEADER -> next.startsWith(FORWARD_FROM)
            else -> false
        }
}

/** The start of the line after [FORWARD_HEADER] in a forward Write made. */
private const val FORWARD_FROM = "From: "

/** The line RFC 3676 puts before a signature, so readers can tell it apart. */
const val SIGNATURE_MARK = "-- "

/** The class of the signature's `<div>` in the HTML Write stores, which the user's text never has. */
const val SIGNATURE_CLASS = "otus-signature"

/** The class of the quote's `<div>` in the HTML Write stores. */
const val QUOTE_CLASS = "otus-quote"

private const val SIGNATURE_OPEN = "<br><div class=\"$SIGNATURE_CLASS\">"

private const val QUOTE_OPEN = "<div class=\"$QUOTE_CLASS\">"
