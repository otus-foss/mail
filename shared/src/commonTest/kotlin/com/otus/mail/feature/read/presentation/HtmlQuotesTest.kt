package com.otus.mail.feature.read.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlQuotesTest {
    /** Margaret's header, whose pill shows [fullDate], as the quote's own line writes it. */
    private fun margaret(fullDate: String = "10 Oct") = QuoteHeader("Margaret Knuth", "m@x.org", "10 Oct", fullDate)

    private val reply = "<div dir=\"ltr\">Sounds good.</div>"
    private val earlier = "<div>I went through the plan.</div>"

    @Test
    fun splitsGmailQuote() {
        val html =
            "$reply<br><div class=\"gmail_quote gmail_quote_container\"><div dir=\"ltr\" class=\"gmail_attr\">" +
                "On Thu, Oct 10, 2026 at 9:14 AM Margaret Knuth " +
                "&lt;<a href=\"mailto:m@x.org\">m@x.org</a>&gt; wrote:<br></div>" +
                "<blockquote class=\"gmail_quote\" style=\"margin:0\">$earlier</blockquote></div>"
        assertEquals(HtmlQuoteSplit("$reply<br>", earlier, margaret("10 Oct 2026, 9:14 AM")), splitHtmlQuote(html))
    }

    @Test
    fun splitsAppleMailCite() {
        val html =
            "<html><body>$reply<div><br><blockquote type=\"cite\">On 10 Oct 2026, at 09:14, Margaret Knuth " +
                "&lt;m@x.org&gt; wrote:<br><br></blockquote></div>" +
                "<blockquote type=\"cite\">$earlier</blockquote></body></html>"
        assertEquals(
            HtmlQuoteSplit("<html><body>$reply<div><br></body></html>", earlier, margaret("10 Oct 2026, 09:14")),
            splitHtmlQuote(html),
        )
    }

    @Test
    fun readsTheAttributionAboveACite() {
        val html =
            "$reply<div>On 10 Oct 2026, at 09:14, Margaret Knuth &lt;m@x.org&gt; wrote:</div><br>" +
                "<blockquote type=\"cite\">$earlier</blockquote>"
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret("10 Oct 2026, 09:14")), splitHtmlQuote(html))
    }

    @Test
    fun keepsNestedBlockquotesInTheQuote() {
        val nested = "$earlier<blockquote type=\"cite\">older</blockquote>"
        val html = "$reply<blockquote type=\"cite\">$nested</blockquote><p>Signature</p>"
        assertEquals(HtmlQuoteSplit("$reply<p>Signature</p>", nested, null), splitHtmlQuote(html))
    }

    @Test
    fun splitsOutlookWebReply() {
        val header =
            "<div id=\"divRplyFwdMsg\" dir=\"ltr\"><font><b>From:</b> Margaret Knuth &lt;m@x.org&gt;<br>" +
                "<b>Sent:</b> Thursday, October 10, 2026 9:14 AM<br><b>Subject:</b> Q4</font><div>&nbsp;</div></div>"
        val html = "$reply<hr style=\"display:inline-block\">$header$earlier"
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret("10 Oct 2026, 9:14 AM")), splitHtmlQuote(html))
    }

    @Test
    fun splitsOutlookDesktopReply() {
        val header =
            "<div style=\"border:none;border-top:solid #E1E1E1 1.0pt\"><p><b>From:</b> Margaret Knuth &lt;m@x.org&gt;" +
                "<br><b>Sent:</b> Thursday, October 10, 2026 9:14 AM</p></div>"
        val split = splitHtmlQuote("$reply$header$earlier")
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret("10 Oct 2026, 9:14 AM")), split)
    }

    @Test
    fun anOutlookQuoteLeavesTheReplysWrapperEndsBehind() {
        val header =
            "<div id=\"divRplyFwdMsg\"><b>From:</b> Margaret Knuth &lt;m@x.org&gt;<br>" +
                "<b>Sent:</b> Thursday, October 10, 2026 9:14 AM</div>"
        val html = "<html><head><style>p{color:red}</style></head><body><div>$reply$header$earlier</div></body></html>"
        assertEquals(earlier, splitHtmlQuote(html)?.quote)
    }

    @Test
    fun wrapperEndsAndTheRuleAboveGoInAnyCaseAndSpacing() {
        val header =
            "<div id=\"divRplyFwdMsg\"><b>From:</b> Margaret Knuth &lt;m@x.org&gt;<br>" +
                "<b>Sent:</b> Thursday, October 10, 2026 9:14 AM</div>"
        val html = "<BODY><DIV>$reply<HR size=1>\n$header$earlier\n</DIV >\n</BODY>\n</HTML>\n"
        assertEquals(
            HtmlQuoteSplit("<BODY><DIV>$reply", earlier, margaret("10 Oct 2026, 9:14 AM")),
            splitHtmlQuote(html),
        )
    }

    @Test
    fun anAttributionInItsOwnElementGoesWithIt() {
        val html =
            "$reply<p class=\"cite\">On 10 Oct 2026, at 09:14, Margaret Knuth &lt;m@x.org&gt; wrote:</p>\n" +
                "<BLOCKQUOTE>$earlier</BLOCKQUOTE>"
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret("10 Oct 2026, 09:14")), splitHtmlQuote(html))
    }

    @Test
    fun stylesAndTitlesAreNotText() {
        assertEquals("Hi", markupText("<head><title>Mail</title><style>p{}</style></head><style>b{}</style><p>Hi</p>"))
        // A style that is never closed hides nothing, but a later title still goes.
        assertEquals("a\nb c d", markupText("<STYLE>x</styles>y</Style >a<br>b <style>c <title>t</title>d"))
    }

    @Test
    fun splitsAPlainBlockquoteAtTheEnd() =
        assertEquals(HtmlQuoteSplit(reply, earlier, null), splitHtmlQuote("$reply<blockquote>$earlier</blockquote>"))

    @Test
    fun keepsABlockquoteInTheMiddle() =
        assertNull(splitHtmlQuote("$reply<blockquote>A saying</blockquote><p>More text</p>"))

    @Test
    fun returnsNullWithoutAQuote() = assertNull(splitHtmlQuote("$reply<p>Thanks</p>"))

    @Test
    fun returnsNullWhenOnlyAQuoteIsLeft() =
        assertNull(splitHtmlQuote("<blockquote type=\"cite\">$earlier</blockquote>"))
}
