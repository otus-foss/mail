package com.otus.mail.feature.compose.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedDraftTest {
    @Test
    fun theTextLosesItsSignatureAndKeepsItsQuoteApart() {
        val text = "Thanks\nOn Monday we meet\n\n-- \nAda\n\nOn Oct 8, Grace wrote:\n> Hi"
        assertEquals(SavedText("Thanks\nOn Monday we meet", "On Oct 8, Grace wrote:\n> Hi"), text.savedParts())
        assertEquals(SavedText("Just text"), "Just text".savedParts())
    }

    @Test
    fun theHtmlBodyEndsOnlyAtWritesOwnMarks() {
        val html = "Thanks<br>On Monday we meet<br><div class=\"otus-signature\"><b>Ada</b></div>"
        assertEquals("Thanks<br>On Monday we meet", html.savedHtmlBody())
        assertEquals(
            "<p>Hi</p>",
            "<p>Hi</p><div class=\"otus-quote\"><br><blockquote>Old</blockquote></div>".savedHtmlBody(),
        )
        assertNull("<p>Hi</p><br><div>Ada</div><br>On Monday, Grace wrote:".savedHtmlBody())
    }
}
