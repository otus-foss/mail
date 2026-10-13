package com.otus.mail.feature.inbox.data

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextTest {
    @Test
    fun aNewsletterReadsAsItsVisibleText() {
        val html =
            """
            <!DOCTYPE html><html><head><meta charset="utf-8"><title>Weekly news</title>
            <style>p { color: red; } a > b { }</style><script>if (a < b) document.write("<p>Hi</p>")</script>
            </head><body><!-- tracking --><table><tr><td><h1>Hello&nbsp;Ada</h1>
            <p>Two    lines<br>in   one paragraph.</p>
            <p>Prices &lt; &euro;5 &amp; more&hellip;</p></td></tr></table></body></html>
            """.trimIndent()
        assertEquals("Hello Ada\n\nTwo lines\nin one paragraph.\n\nPrices < €5 & more…", html.htmlToText())
    }

    @Test
    fun blocksStartNewLinesAndCellsStayApart() {
        val html =
            "<div>One</div><div>Two</div>Three<ul><li>Four</li><li>Five</li></ul><hr>Six" +
                "<table><tr><td>A</td><td>B</td></tr><tr><th>C</th></tr></table>"
        assertEquals("One\nTwo\nThree\n\nFour\nFive\n\nSix\n\nA B\nC", html.htmlToText())
    }

    @Test
    fun breaksNeverLeaveMoreThanOneBlankLine() {
        assertEquals("Line\n\nGap", "Line<br><br>Gap".htmlToText())
        assertEquals("Hi\n\nthere", "<br><p>Hi</p><br><br><br><p></p><div></div>there<br>".htmlToText())
        assertEquals("Hello world again", "  Hello\n   world\r\n\tagain  ".htmlToText())
    }

    @Test
    fun characterReferencesAreDecodedOnceAndNeverBecomeMarkup() {
        assertEquals("é é é 😀 😀", "&#233; &#xE9; &#XE9 &#128512; &#x1F600;".htmlToText())
        assertEquals(List(4) { "\uFFFD" }.joinToString(" "), "&#0; &#xD800; &#x110000; &#99999999999;".htmlToText())
        assertEquals("<b>bold</b> &amp;", "&lt;b&gt;bold&lt;/b&gt; &amp;amp;".htmlToText())
        // Browsers read 128 to 159 as Windows-1252, which old mail tools still write for quotes and dashes.
        assertEquals("don’t – “q” €5 Ÿ", "don&#146;t &#150; &#147;q&#148; &#x80;5 &#159;".htmlToText())
        assertEquals(
            "AT&T &copy2026 &unknown; a=1&b=2 & &; &#",
            "AT&T &copy2026 &unknown; a=1&b=2 & &; &#".htmlToText(),
        )
    }

    @Test
    fun aQuotedGreaterThanSignDoesNotEndATag() {
        val html = """<a href="https://x.example/?a>b" title='1 > 0'>Link</a> <span title=it's>ok</span>"""
        assertEquals("Link ok", html.htmlToText())
    }

    @Test
    fun commentsAndHiddenElementsDropTheirContent() {
        assertEquals("xyw", "<!-->x<!--->y<!-- z -->w".htmlToText())
        assertEquals(
            "Everyone",
            "<!--[if mso]><table><tr><td>Outlook</td></tr></table><![endif]-->Everyone".htmlToText(),
        )
        assertEquals("Shown", "<!--[if !mso]><!-->Shown<!--<![endif]-->".htmlToText())
        assertEquals("c", "<STYLE>a</styles>b</Style >c".htmlToText())
        assertEquals("1 < 2 and 3 <= 4", "1 < 2 and 3 <= 4".htmlToText())
    }

    @Test
    fun markupCutOffAtTheEndIsDropped() {
        listOf("Start<a href=\"https://example", "Start<!-- never closed", "Start<style>p{}", "Start<title>T")
            .forEach { assertEquals("Start", it.htmlToText(), it) }
        assertEquals("Start&am", "Start&am".htmlToText())
    }

    @Test
    fun hugeMalformedMarkupIsReadInOnePass() {
        // Each would take minutes if a search restarted at every position.
        val dropped = listOf("<!--", "<a title='", "<style>", "</styl", "<x", "<!x", "<p>")
        dropped.forEach { assertEquals("", it.repeat(REPEATS).htmlToText(), it) }
        val kept = listOf("&#", "&amp", "<")
        kept.forEach { assertEquals(it.repeat(REPEATS).length, it.repeat(REPEATS).htmlToText().length, it) }
    }

    private companion object {
        const val REPEATS = 100_000
    }
}
