package com.otus.mail.feature.inbox.presentation.read

/**
 * Outlook: a `divRplyFwdMsg` div, or a div with a top border, holds the From and Sent lines; the
 * quoted message is everything after it, along with an `<hr>` right above it. The end tags of the
 * wrappers around the whole reply, such as `</div></body></html>`, are not part of the quote.
 */
internal fun outlookQuote(html: String): HtmlQuoteSplit? {
    val start =
        (OUTLOOK_WEB.find(html) ?: OUTLOOK_DESKTOP.findAll(html).firstOrNull { fieldsAt(html, it.range.first) != null })
            ?.range
            ?.first ?: return null
    val (_, end) = elementEnd(html, start, "div")
    val header = fieldsAt(html, start)
    val cut = HR_BEFORE.find(html.substring(0, start))?.range?.first ?: start
    return HtmlQuoteSplit(html.substring(0, cut), withoutWrapperEnds(html.substring(end)), header)
}

/** [quote] without the end tags of elements that opened before it, such as the reply's own `</div></body>`. */
private fun withoutWrapperEnds(quote: String): String {
    var rest = quote.replace(DOCUMENT_END, "")
    while (true) {
        val end = TRAILING_DIV_END.find(rest) ?: return rest
        if (DIV_START.findAll(rest).count() >= DIV_END.findAll(rest).count()) return rest
        rest = rest.substring(0, end.range.first)
    }
}

/** The header in the From and Sent lines of the div opening at [start]; null when it holds none. */
private fun fieldsAt(
    html: String,
    start: Int,
): QuoteHeader? {
    val (closeStart, _) = elementEnd(html, start, "div")
    return fieldsHeader(markupText(innerHtml(html, start, closeStart)).lines())
}

private val OUTLOOK_WEB = Regex("""<div\b[^>]*\bid\s*=\s*["']?divRplyFwdMsg""", RegexOption.IGNORE_CASE)
private val OUTLOOK_DESKTOP = Regex("""<div\b[^>]*border-top:\s*solid[^>]*>""", RegexOption.IGNORE_CASE)
private val DOCUMENT_END = Regex("""(?:\s*</(?:body|html)\s*>)+\s*$""", RegexOption.IGNORE_CASE)
private val TRAILING_DIV_END = Regex("""\s*</div\s*>\s*$""", RegexOption.IGNORE_CASE)
private val DIV_START = Regex("""<div\b""", RegexOption.IGNORE_CASE)
private val DIV_END = Regex("""</div\s*>""", RegexOption.IGNORE_CASE)
private val HR_BEFORE = Regex("""<hr\b[^>]*>\s*$""", RegexOption.IGNORE_CASE)
