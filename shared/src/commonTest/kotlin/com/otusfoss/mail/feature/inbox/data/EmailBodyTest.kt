package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailBodyValue
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.feature.read.data.toMessageContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailBodyTest {
    private fun message(vararg parts: Pair<String?, EmailBodyValue>): MessageContent =
        EmailObject(
            id = "e1",
            receivedAt = "2026-10-01T12:00:00Z",
            textBody = parts.mapIndexed { index, (type, _) -> EmailBodyPart("$index", type = type) },
            bodyValues = parts.mapIndexed { index, (_, value) -> "$index" to value }.toMap(),
        ).toMessageContent()

    @Test
    fun onlyHtmlPartsLoseTheirMarkup() {
        // A mixed message: an HTML part, then a plain footer whose text looks like markup.
        val body =
            message(
                "text/html" to EmailBodyValue("<p>Agenda &amp; notes</p>"),
                "text/plain" to EmailBodyValue("Answer <yes> or <no> &amp; sign."),
                null to EmailBodyValue("<b>untyped</b>"),
                "TEXT/HTML" to EmailBodyValue("<i>Thanks</i>"),
            ).text
        assertEquals("Agenda & notes\nAnswer <yes> or <no> &amp; sign.\n<b>untyped</b>\nThanks", body)
    }

    @Test
    fun hiddenCharactersInReferencesAreRemovedToo() {
        assertEquals("PayPal", message("text/html" to EmailBodyValue("<p>Pay&#x202E;Pal&#8206;</p>")).text)
    }

    @Test
    fun aCutOffBodyIsMarkedEvenWhenNoTextArrived() {
        val cut = EmailBodyValue("<html><head><style>body { margin: 0", isTruncated = true)
        val message = message("text/html" to cut)
        assertEquals("", message.text)
        assertTrue(message.truncated)
        assertFalse(message("text/plain" to EmailBodyValue("All of it")).truncated)
    }

    @Test
    fun aPartCutOffBeforeMoreTextIsMarkedWhereItStops() {
        val gap = "[Part of the message is missing here.]"
        val cut = EmailBodyValue("Minutes: we agre", isTruncated = true)
        val middle = message("text/plain" to cut, "text/plain" to EmailBodyValue("Ada"))
        assertEquals("Minutes: we agre\n$gap\nAda", middle.text)
        assertFalse(middle.truncated, "The end is all there, so nothing says it is missing")
        val both = message("text/plain" to cut, "text/plain" to EmailBodyValue("Ad", isTruncated = true))
        assertEquals("Minutes: we agre\n$gap\nAd", both.text)
        assertTrue(both.truncated)
        val last = message("text/plain" to cut, "text/html" to EmailBodyValue("<br>"))
        assertFalse(gap in last.text, "No text follows, so the cut is the end")
        assertTrue(last.truncated)
    }
}
