package com.otus.mail.feature.inbox.presentation.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlainQuotesTest {
    private val margaret = QuoteHeader("Margaret Knuth", "m@x.org", "10 Oct")
    private val reply = "Sounds good, Thursday 10 it is.\n\nKatherine"
    private val quoted = "> I went through the plan with legal.\n>\n> > Earlier text"

    @Test
    fun splitsAtAppleMailHeader() {
        val text = "$reply\n\nOn 10 Oct 2026, at 09:14, Margaret Knuth <m@x.org> wrote:\n\n$quoted"
        assertEquals(
            PlainQuote(reply, margaret, "I went through the plan with legal.\n\n> Earlier text"),
            splitPlainQuote(text),
        )
    }

    @Test
    fun splitsAtGmailHeader() {
        val text = "$reply\n\nOn Thu, Oct 10, 2026 at 9:14 AM Margaret Knuth <m@x.org> wrote:\n$quoted"
        assertEquals(margaret, splitPlainQuote(text)?.header)
        assertEquals(reply, splitPlainQuote(text)?.body)
    }

    @Test
    fun readsAHeaderWrappedOverTwoLines() {
        val text = "$reply\n\nOn Thu, Oct 10, 2026 at 9:14 AM Margaret Knuth\n<m@x.org> wrote:\n$quoted"
        assertEquals(margaret, splitPlainQuote(text)?.header)
    }

    @Test
    fun readsAHeaderWithoutAddress() {
        val text = "$reply\n\nOn 10 Oct 2026, Margaret Knuth wrote:\n$quoted"
        assertEquals(QuoteHeader("Margaret Knuth", null, "10 Oct"), splitPlainQuote(text)?.header)
    }

    @Test
    fun readsAGermanHeader() {
        val text = "$reply\n\nAm 10.10.2026 um 09:14 schrieb Margaret Knuth <m@x.org>:\n$quoted"
        assertEquals(margaret, splitPlainQuote(text)?.header)
    }

    @Test
    fun keepsADateItCannotRead() {
        val text = "$reply\n\nOn Thursday, Margaret Knuth wrote:\n$quoted"
        assertEquals(QuoteHeader("Margaret Knuth", null, "Thursday"), splitPlainQuote(text)?.header)
    }

    @Test
    fun splitsMarkedLinesThatRunToTheEnd() {
        val split = splitPlainQuote("$reply\n\n$quoted\n")
        assertEquals(PlainQuote(reply, null, "I went through the plan with legal.\n\n> Earlier text"), split)
    }

    @Test
    fun keepsRepliesWrittenBetweenQuotedLines() = assertNull(splitPlainQuote("> Can you?\nYes.\n> And Friday?\nNo."))

    @Test
    fun splitsAtOutlookOriginalMessage() {
        val text =
            "$reply\n\n-----Original Message-----\nFrom: Margaret Knuth <m@x.org>\n" +
                "Sent: Thursday, October 10, 2026 9:14 AM\nTo: Katherine\nSubject: Q4\n\nI went through the plan."
        assertEquals(PlainQuote(reply, margaret, "I went through the plan."), splitPlainQuote(text))
    }

    @Test
    fun returnsNullWithoutAQuote() = assertNull(splitPlainQuote(reply))

    @Test
    fun returnsNullWhenOnlyAQuoteIsLeft() = assertNull(splitPlainQuote("On 10 Oct 2026, Ada wrote:\n> Hi"))
}
