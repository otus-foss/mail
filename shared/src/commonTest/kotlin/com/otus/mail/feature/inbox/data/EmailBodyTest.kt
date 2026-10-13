package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailMessage
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailBodyTest {
    private fun message(
        vararg parts: Pair<String?, EmailBodyValue>,
        preview: String = "",
    ): MailMessage =
        EmailObject(
            id = "e1",
            receivedAt = "2026-10-01T12:00:00Z",
            preview = preview,
            textBody = parts.mapIndexed { index, (type, _) -> EmailBodyPart("$index", type = type) },
            bodyValues = parts.mapIndexed { index, (_, value) -> "$index" to value }.toMap(),
        ).toMessage("work", "Ada", "Inbox", TimeZone.UTC)

    @Test
    fun onlyHtmlPartsLoseTheirMarkup() {
        // A mixed message: an HTML part, then a plain footer whose text looks like markup.
        val body =
            message(
                "text/html" to EmailBodyValue("<p>Agenda &amp; notes</p>"),
                "text/plain" to EmailBodyValue("Answer <yes> or <no> &amp; sign."),
                null to EmailBodyValue("<b>untyped</b>"),
                "TEXT/HTML" to EmailBodyValue("<i>Thanks</i>"),
            ).body
        assertEquals("Agenda & notes\nAnswer <yes> or <no> &amp; sign.\n<b>untyped</b>\nThanks", body)
    }

    @Test
    fun hiddenCharactersInReferencesAreRemovedToo() {
        assertEquals("PayPal", message("text/html" to EmailBodyValue("<p>Pay&#x202E;Pal&#8206;</p>")).body)
    }

    @Test
    fun aCutOffBodyIsMarkedEvenWhenOnlyThePreviewShows() {
        val cut = EmailBodyValue("<html><head><style>body { margin: 0", isTruncated = true)
        val message = message("text/html" to cut, preview = "Big sale")
        assertEquals("Big sale", message.body)
        assertTrue(message.bodyTruncated)
        assertFalse(message("text/plain" to EmailBodyValue("All of it")).bodyTruncated)
    }

    @Test
    fun aPartCutOffBeforeMoreTextIsMarkedWhereItStops() {
        val gap = "[Part of the message is missing here.]"
        val cut = EmailBodyValue("Minutes: we agre", isTruncated = true)
        val middle = message("text/plain" to cut, "text/plain" to EmailBodyValue("Ada"))
        assertEquals("Minutes: we agre\n$gap\nAda", middle.body)
        assertFalse(middle.bodyTruncated, "The end is all there, so nothing says it is missing")
        val both = message("text/plain" to cut, "text/plain" to EmailBodyValue("Ad", isTruncated = true))
        assertEquals("Minutes: we agre\n$gap\nAd", both.body)
        assertTrue(both.bodyTruncated)
        val last = message("text/plain" to cut, "text/html" to EmailBodyValue("<br>"))
        assertFalse(gap in last.body, "No text follows, so the cut is the end")
        assertTrue(last.bodyTruncated)
    }
}
