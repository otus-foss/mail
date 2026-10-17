package com.otus.mail.feature.inbox.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyQuoteTest {
    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Q4 launch plan",
            from = MailSender("Grace Hopper", "grace@example.com"),
            to = emptyList(),
            cc = emptyList(),
            sentLabel = "2026-10-11 09:12",
            body = "Can we move it?\n\nOn 2026-10-10 08:00, Ada wrote:\n> Works for me.",
        )

    @Test
    fun theWholeThreadQuotesTheOriginalWithWhatItQuotes() {
        val quote = original.replyQuote(QuoteMode.Thread)
        assertEquals(
            "On 2026-10-11 09:12, Grace Hopper wrote:\n> Can we move it?\n>\n" +
                "> On 2026-10-10 08:00, Ada wrote:\n> > Works for me.",
            quote,
        )
    }

    @Test
    fun latestOnlyStopsWhereTheOriginalStartsQuotingAndNoneQuotesNothing() {
        assertEquals(
            "On 2026-10-11 09:12, Grace Hopper wrote:\n> Can we move it?",
            original.replyQuote(QuoteMode.Latest),
        )
        assertEquals("", original.replyQuote(QuoteMode.None))
    }
}
