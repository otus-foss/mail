package com.otus.mail.feature.inbox.presentation.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlQuotesTest {
    private val margaret = QuoteHeader("Margaret Knuth", "m@x.org", "10 Oct")
    private val reply = "<div dir=\"ltr\">Sounds good.</div>"
    private val earlier = "<div>I went through the plan.</div>"

    @Test
    fun splitsGmailQuote() {
        val html =
            "$reply<br><div class=\"gmail_quote gmail_quote_container\"><div dir=\"ltr\" class=\"gmail_attr\">" +
                "On Thu, Oct 10, 2026 at 9:14 AM Margaret Knuth " +
                "&lt;<a href=\"mailto:m@x.org\">m@x.org</a>&gt; wrote:<br></div>" +
                "<blockquote class=\"gmail_quote\" style=\"margin:0\">$earlier</blockquote></div>"
        assertEquals(HtmlQuoteSplit("$reply<br>", earlier, margaret), splitHtmlQuote(html))
    }

    @Test
    fun splitsAppleMailCite() {
        val html =
            "<html><body>$reply<div><br><blockquote type=\"cite\">On 10 Oct 2026, at 09:14, Margaret Knuth " +
                "&lt;m@x.org&gt; wrote:<br><br></blockquote></div>" +
                "<blockquote type=\"cite\">$earlier</blockquote></body></html>"
        assertEquals(
            HtmlQuoteSplit("<html><body>$reply<div><br></body></html>", earlier, margaret),
            splitHtmlQuote(html),
        )
    }

    @Test
    fun readsTheAttributionAboveACite() {
        val html =
            "$reply<div>On 10 Oct 2026, at 09:14, Margaret Knuth &lt;m@x.org&gt; wrote:</div><br>" +
                "<blockquote type=\"cite\">$earlier</blockquote>"
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret), splitHtmlQuote(html))
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
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret), splitHtmlQuote(html))
    }

    @Test
    fun splitsOutlookDesktopReply() {
        val header =
            "<div style=\"border:none;border-top:solid #E1E1E1 1.0pt\"><p><b>From:</b> Margaret Knuth &lt;m@x.org&gt;" +
                "<br><b>Sent:</b> Thursday, October 10, 2026 9:14 AM</p></div>"
        val split = splitHtmlQuote("$reply$header$earlier")
        assertEquals(HtmlQuoteSplit(reply, earlier, margaret), split)
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
