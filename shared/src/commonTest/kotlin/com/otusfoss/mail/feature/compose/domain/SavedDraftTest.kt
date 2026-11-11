package com.otusfoss.mail.feature.compose.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedDraftTest {
    @Test
    fun theTextSplitsIntoBodySignatureAndQuote() {
        val text = "Thanks\nOn Monday we meet\n\n-- \nAda\n\nOn Oct 8, Grace wrote:\n> Hi"
        assertEquals(
            SavedText("Thanks\nOn Monday we meet", "On Oct 8, Grace wrote:\n> Hi", signature = "Ada"),
            text.savedParts(),
        )
        assertEquals(SavedText("Just text"), "Just text".savedParts())
        assertEquals(
            SavedText("Hi\n\nOn Monday we wrote:\nthe plan", signature = "Ada"),
            "Hi\n\nOn Monday we wrote:\nthe plan\n\n-- \nAda".savedParts(),
        )
    }

    @Test
    fun theHtmlBodyEndsOnlyAtWritesOwnMarks() {
        val html = "Thanks<br>On Monday we meet<br><div class=\"otus-signature\"><b>Ada</b></div>"
        assertEquals("Thanks<br>On Monday we meet", html.savedHtmlBody())
        assertEquals("<b>Ada</b>", html.savedHtmlSignature())
        assertEquals("", "<p>Hi</p>".savedHtmlSignature())
        assertEquals(
            "",
            "<p>Hi</p><div class=\"otus-quote\"><br><div class=\"otus-signature\">x</div></div>".savedHtmlSignature(),
        )
        assertEquals(
            "<p>Hi</p>",
            "<p>Hi</p><div class=\"otus-quote\"><br><blockquote>Old</blockquote></div>".savedHtmlBody(),
        )
        assertNull("<p>Hi</p><br><div>Ada</div><br>On Monday, Grace wrote:".savedHtmlBody())
    }
}
