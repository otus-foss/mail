package com.otusfoss.mail.feature.read.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainPageTest {
    @Test
    fun aLongTextLaysOutInPiecesOfWholeLines() {
        val log = (1..12_000).joinToString("\n") { "step $it: compiled module $it without warnings" }
        assertTrue(log.length > 500_000)
        val page = plainPage(log)
        assertTrue(page.chunks.size > 1, "A long text is not one piece")
        assertTrue(page.chunks.all { it.length < 10_000 }, "Each piece is short enough to lay out quickly")
        assertEquals(log, page.chunks.joinToString("\n"), "The pieces keep every line, blank or not")
        assertTrue(page.quoteLevels.isEmpty())
    }

    @Test
    fun blankLinesAndTheQuoteSurviveTheSplit() {
        val text = "Thanks.\n\n\nKatherine\n\nOn 10 Oct 2026, at 09:14, Margaret Knuth <m@x.example> wrote:\n> The plan"
        val page = plainPage(text)
        assertEquals(listOf("Thanks.\n\n\nKatherine"), page.chunks)
        assertEquals(listOf("The plan"), page.quoteLevels.map { it.text })
    }

    @Test
    fun anEmptyTextIsOneEmptyPiece() {
        assertEquals(listOf(""), textChunks(""))
    }
}
