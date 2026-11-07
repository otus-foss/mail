package com.otus.mail.feature.read.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** Hostile mail must not stall the Read view: each case took from seconds to hours when a search restarted. */
class HugeQuoteMarkupTest {
    private val reply = "<div dir=\"ltr\">Sounds good.</div>"
    private val earlier = "<div>I went through the plan.</div>"

    @Test
    fun unclosedTagsAfterAQuoteAreReadInOnePass() =
        quickly {
            assertNull(splitHtmlQuote("hi<blockquote>q</blockquote>" + "<".repeat(REPEATS)))
            assertEquals(REPEATS, markupText("<".repeat(REPEATS)).length)
            listOf("<style>", "<head><title>", "<p>").forEach {
                assertEquals("", markupText(it.repeat(REPEATS / it.length)), it)
            }
            // Without a `>`, nothing is a tag, so the text stays.
            listOf("<div ", "<blockquote ").map { it.repeat(REPEATS / it.length) }.forEach {
                assertEquals(it.trim(), markupText(it))
            }
            listOf("<div", "<blockquote", "<hr", "<div class='gmail_quote'", "<blockquote type=cite").forEach {
                assertNull(splitHtmlQuote("$reply${it.repeat(REPEATS / it.length)}"), it)
            }
        }

    @Test
    fun outlookMarkupIsReadInOnePass() =
        quickly {
            val bordered = "<div style=\"border-top:solid\">"
            assertNull(splitHtmlQuote(reply + bordered.repeat(REPEATS / bordered.length)))
            val header = "<div id=\"divRplyFwdMsg\"><b>From:</b> Margaret Knuth &lt;m@x.org&gt;</div>"
            val wrapped = "$reply$header$earlier</div>" + " ".repeat(REPEATS) + "x"
            assertTrue(splitHtmlQuote(wrapped)?.quote.orEmpty().endsWith(" x"))
            val ends = "$reply$header$earlier" + "</div>".repeat(REPEATS / 6)
            assertEquals(earlier, splitHtmlQuote(ends)?.quote)
        }

    @Test
    fun longAttributionsAndSeparatorsAreReadInOnePass() =
        quickly {
            val dashes = "-".repeat(REPEATS)
            assertEquals(reply + dashes, splitHtmlQuote("$reply$dashes<blockquote>$earlier</blockquote>")?.main)
            val wrote = "On wrote<".repeat(REPEATS / 9)
            assertEquals(earlier, splitHtmlQuote("$reply$wrote<blockquote>$earlier</blockquote>")?.quote)
            val many = "On a wrote:".repeat(REPEATS / 11)
            val split = splitHtmlQuote("$reply$many<blockquote>$earlier</blockquote>")
            assertEquals(QuoteHeader("a", null, null), split?.header)
            assertNull(attributionHeader("Am x" + " schrieb".repeat(REPEATS / 8) + ":"))
            assertNull(attributionHeader("On x <" + "a@".repeat(REPEATS / 2) + " wrote:"))
        }

    @Test
    fun quotedLinesBeforeAReplyAreReadInOnePass() =
        quickly {
            assertNull(splitPlainQuote("> a\n".repeat(REPEATS / 4) + "Reply"))
            assertEquals(REPEATS / 4, splitPlainQuote("Reply\n" + "> a\n".repeat(REPEATS / 4))?.quoted?.lines()?.size)
        }

    private fun quickly(check: () -> Unit) {
        val took = measureTime(check)
        assertTrue(took < LIMIT, "Took $took")
    }

    private companion object {
        const val REPEATS = 200_000
        val LIMIT = 10.seconds
    }
}
