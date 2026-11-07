package com.otus.mail.feature.read.presentation

/**
 * Outlook: a `divRplyFwdMsg` div, or a div with a top border, holds the From and Sent lines; the
 * quoted message is everything after it, along with an `<hr>` right above it. The end tags of the
 * wrappers around the whole reply, such as `</div></body></html>`, are not part of the quote. Only the
 * first [MAX_BORDERED_DIVS] bordered divs are read for the lines, so a message full of them stays quick.
 */
internal fun outlookQuote(html: String): HtmlQuoteSplit? {
    val start =
        startTags(html, "div", OUTLOOK_WEB).firstOrNull()
            ?: startTags(
                html,
                "div",
                OUTLOOK_DESKTOP,
            ).take(MAX_BORDERED_DIVS).firstOrNull { fieldsAt(html, it) != null }
            ?: return null
    val (_, end) = elementEnd(html, start, "div")
    val header = fieldsAt(html, start)
    val cut = startTagBefore(html, start, HR) ?: start
    return HtmlQuoteSplit(html.substring(0, cut), withoutWrapperEnds(html.substring(end)), header)
}

/**
 * [quote] without the end tags of elements that opened before it, such as the reply's own `</div></body>`.
 * The divs are counted once, and each trailing end tag is read back from the end.
 */
private fun withoutWrapperEnds(quote: String): String {
    val rest = quote.substring(0, generateSequence(quote.length) { trailingEndTag(quote, it, DOCUMENT) }.last())
    val opened = DIV_START.findAll(rest).count()
    var closed = DIV_END.findAll(rest).count()
    var end = rest.length
    while (closed > opened) {
        end = trailingEndTag(rest, end, DIV) ?: break
        closed--
    }
    return rest.substring(0, end)
}

/** The header in the From and Sent lines of the div opening at [start]; null when it holds none. */
private fun fieldsAt(
    html: String,
    start: Int,
): QuoteHeader? {
    val (closeStart, _) = elementEnd(html, start, "div")
    return fieldsHeader(markupText(innerHtml(html, start, closeStart)).lines())
}

/** In a `<div>` start tag: the id Outlook on the web gives the header. */
private val OUTLOOK_WEB = Regex("""\bid\s*=\s*["']?divRplyFwdMsg""", RegexOption.IGNORE_CASE)

/** In a `<div>` start tag: the top border Outlook for desktop draws above the header. */
private val OUTLOOK_DESKTOP = Regex("""border-top:\s*solid""", RegexOption.IGNORE_CASE)
private val DOCUMENT = listOf("body", "html")
private val DIV = listOf("div")
private val DIV_START = Regex("""<div\b""", RegexOption.IGNORE_CASE)
private val DIV_END = Regex("""</div\s*>""", RegexOption.IGNORE_CASE)
private val HR = Regex("""<hr\b""", RegexOption.IGNORE_CASE)

/** How many divs with a top border are read for Outlook's From and Sent lines. */
private const val MAX_BORDERED_DIVS = 8
