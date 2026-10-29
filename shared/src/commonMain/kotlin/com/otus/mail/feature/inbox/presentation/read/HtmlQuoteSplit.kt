package com.otus.mail.feature.inbox.presentation.read

/**
 * An HTML reply split for the Read view's quote fold: the [main] part the sender wrote, the
 * [quote] markup, and the [header] naming who wrote the quoted message, when found.
 */
data class HtmlQuoteSplit(
    val main: String,
    val quote: String,
    val header: QuoteHeader?,
)

/**
 * [html] split where it quotes an earlier message, in the markup mail apps write: Gmail's
 * `gmail_quote` block, a `<blockquote type="cite">` as Apple Mail and Thunderbird write it, the
 * header Outlook writes above the quote, or a first blockquote that nothing visible follows. The
 * "On … wrote:" line above a blockquote becomes the [HtmlQuoteSplit.header]. Null when none is
 * found or nothing visible comes before the quote.
 */
fun splitHtmlQuote(html: String): HtmlQuoteSplit? =
    (gmailQuote(html) ?: citeQuote(html) ?: outlookQuote(html) ?: lastBlockquote(html))
        ?.let { it.copy(main = withoutTrailingSeparator(it.main)) }
        ?.takeIf { markupText(it.main).isNotBlank() && markupText(it.quote).isNotBlank() }

/**
 * [main] without an "Original Message" line at its end, which Proton Mail writes above the quote;
 * the fold's header names the quote instead.
 */
private fun withoutTrailingSeparator(main: String): String {
    val separator = SEPARATOR.findAll(main).lastOrNull() ?: return main
    val after = main.substring(separator.range.last + 1)
    return if (markupText(after).isBlank()) main.removeRange(separator.range) else main
}

/** Gmail: the attribution line and the blockquote sit together in one `gmail_quote` div. */
private fun gmailQuote(html: String): HtmlQuoteSplit? {
    val start = GMAIL.find(html)?.range?.first ?: return null
    val (closeStart, end) = elementEnd(html, start, "div")
    val inner = innerHtml(html, start, closeStart)
    val blockquote = BLOCKQUOTE.find(inner)?.range?.first
    val header = blockquote?.let { attributionIn(inner.substring(0, it)) }
    val quote = blockquote?.let { innerBlockquote(inner, it) } ?: inner
    return HtmlQuoteSplit(html.substring(0, start) + html.substring(end), quote, header)
}

/**
 * A cite blockquote. Apple Mail puts the attribution line in a cite blockquote of its own and the
 * quoted message in the next one; then both go and the line becomes the header.
 */
private fun citeQuote(html: String): HtmlQuoteSplit? {
    val start = CITE.find(html)?.range?.first ?: return null
    val header = attributionIn(innerBlockquote(html, start))
    val next = header?.let { CITE.find(html, elementEnd(html, start, "blockquote").second) }?.range?.first
    return if (next == null) {
        blockquoteSplit(html, start)
    } else {
        val end = elementEnd(html, next, "blockquote").second
        HtmlQuoteSplit(html.substring(0, start) + html.substring(end), innerBlockquote(html, next), header)
    }
}

/** A plain blockquote counts only when it closes the message, so a quoted passage in the middle stays. */
private fun lastBlockquote(html: String): HtmlQuoteSplit? {
    val start = BLOCKQUOTE.find(html)?.range?.first ?: return null
    val (_, end) = elementEnd(html, start, "blockquote")
    return blockquoteSplit(html, start).takeIf { markupText(html.substring(end)).isBlank() }
}

/** The blockquote at [start] as the quote, with an attribution line right above it as the header. */
private fun blockquoteSplit(
    html: String,
    start: Int,
): HtmlQuoteSplit {
    val (_, end) = elementEnd(html, start, "blockquote")
    val before = html.substring(0, start)
    val attribution =
        ATTRIBUTION
            .findAll(before)
            .lastOrNull { markupText(before.substring(it.range.last + 1)).isBlank() }
    val cut = attribution?.let { openingTagBefore(before, it.range.first) } ?: start
    val header = attribution?.let { attributionIn(it.value) }
    return HtmlQuoteSplit(html.substring(0, cut) + html.substring(end), innerBlockquote(html, start), header)
}

/** The header in the attribution line [html] shows, read as one line. */
private fun attributionIn(html: String): QuoteHeader? = attributionHeader(markupText(html).replace('\n', ' '))

private fun innerBlockquote(
    html: String,
    start: Int,
): String = innerHtml(html, start, elementEnd(html, start, "blockquote").first)

/** [index], moved back over an opening tag right before it, so the attribution's own element goes too. */
private fun openingTagBefore(
    html: String,
    index: Int,
): Int = OPENING_TAG_END.find(html.substring(0, index))?.range?.first ?: index

private val GMAIL = Regex("""<div\b[^>]*\bclass\s*=\s*["'][^"']*\bgmail_quote""", RegexOption.IGNORE_CASE)
private val BLOCKQUOTE = Regex("""<blockquote\b""", RegexOption.IGNORE_CASE)
private val CITE = Regex("""<blockquote\b[^>]*\btype\s*=\s*["']?cite""", RegexOption.IGNORE_CASE)
private val OPENING_TAG_END = Regex("""<(?:div|p|span)\b[^>]*>\s*$""", RegexOption.IGNORE_CASE)

/** "On … wrote:" or "Am … schrieb …:" with inline tags such as a mailto link, but no block tags. */
private val ATTRIBUTION =
    Regex(
        """\b(?:On|Am)\s(?:(?!</?(?:div|p|blockquote|br)\b)[\s\S]){1,500}?""" +
            """(?:wrote|schrieb[^<:]{0,200})(?:<[^>]*>)*:""",
    )
private val SEPARATOR = Regex(ORIGINAL_MESSAGE, RegexOption.IGNORE_CASE)
