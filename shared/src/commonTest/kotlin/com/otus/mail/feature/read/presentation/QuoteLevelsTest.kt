package com.otus.mail.feature.read.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class QuoteLevelsTest {
    @Test
    fun sixNestedLevelsSplitIntoSixLevels() {
        val reply =
            """
            Reply

            On Sat, 17 Oct 2026 at 17:56, Ada Lovelace <ada@example.org> wrote:
            > five
            >
            > On Sat, 17 Oct 2026 at 17:55, Ada Lovelace <ada@example.org> wrote:
            >> four
            >>
            >> On Sat, 17 Oct 2026 at 17:54, Ada Lovelace <ada@example.org> wrote:
            >>> three
            >>>
            >>> On Sat, 17 Oct 2026 at 17:53, Ada Lovelace <ada@example.org> wrote:
            >>>> two
            >>>>
            >>>> On Sat, 17 Oct 2026 at 17:52, Ada Lovelace <ada@example.org> wrote:
            >>>>> one
            >>>>>
            >>>>> On Sat, 17 Oct 2026 at 17:51, Ada Lovelace <ada@example.org> wrote:
            >>>>>> zero
            """.trimIndent()
        val levels = requireNotNull(splitPlainQuote(reply)).levels()
        assertEquals(listOf("five", "four", "three", "two", "one", "zero"), levels.map { it.text }, "$levels")
        assertEquals(
            listOf("17:56", "17:55", "17:54", "17:53", "17:52", "17:51"),
            levels.map { it.header?.fullDate?.substringAfter(", ") },
        )
    }

    @Test
    fun protonQuotesInAnHtmlQuoteSplitAtEachOriginalMessage() {
        // The text of an HTML quote as markupText reads it: no blank lines, each line trimmed.
        val text =
            """
            fjdjss
            -------- Original Message --------
            On Saturday, 10/17/26 at 17:50 Florian Ruby <florian@purbank.ch> wrote:
            > jdjchcbeb
            >
            > On 2026-10-17 17:33, Florian Ruby wrote:
            >
            >> kejesb
            >>
            >> Sent from [Proton Mail](https://proton.me/mail/home) for Android.
            >>
            >> -------- Original Message --------
            >> On Saturday, 10/17/26 at 17:31 Florian Ruby <ruby.florian@proton.me> wrote:
            >>
            >>> dhisisxh
            """.trimIndent()
        val levels = quoteLevels(QuoteHeader("Florian Ruby", null, "17 Oct", "17 Oct 2026, 17:52"), text)
        assertEquals(4, levels.size, "$levels")
        assertEquals(
            listOf(
                "17 Oct 2026, 17:52",
                "17 Oct 2026, 17:50",
                "17 Oct 2026, 17:33",
                "17 Oct 2026, 17:31",
            ),
            levels.map {
                it.header?.fullDate
            },
        )
        assertEquals("dhisisxh", levels.last().text)
    }
}
