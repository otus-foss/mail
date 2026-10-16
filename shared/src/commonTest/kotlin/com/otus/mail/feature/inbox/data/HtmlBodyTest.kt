package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MessageHtml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlBodyTest {
    private fun html(vararg parts: Pair<String?, EmailBodyValue?>): MessageHtml? =
        EmailObject(
            id = "e1",
            receivedAt = "2026-10-01T12:00:00Z",
            htmlBody = parts.mapIndexed { index, (type, _) -> EmailBodyPart("$index", type = type) },
            bodyValues =
                parts
                    .withIndex()
                    .mapNotNull { (index, part) -> part.second?.let { "$index" to it } }
                    .toMap(),
        ).toMessageHtml()

    @Test
    fun plainMessagesHaveNoHtml() {
        assertNull(html("text/plain" to EmailBodyValue("Hello")))
        assertNull(EmailObject(id = "e1", receivedAt = "2026-10-01T12:00:00Z").toMessageHtml())
    }

    @Test
    fun plainPartsAreEscapedAndKeepTheirLines() {
        val body =
            html(
                "text/html" to EmailBodyValue("<p>Agenda</p>"),
                "text/plain" to EmailBodyValue("Answer <yes> & sign\nbelow"),
                "image/png" to null,
            )
        assertEquals(
            "<p>Agenda</p>\n<div style=\"white-space:pre-wrap\">Answer &lt;yes&gt; &amp; sign\nbelow</div>",
            body?.html,
        )
        assertFalse(body!!.truncated)
    }

    @Test
    fun findsRemoteImagesButNotLinks() {
        assertFalse(html("TEXT/HTML" to EmailBodyValue("<a href=\"https://otus.example\">Open</a>"))!!.remoteContent)
        listOf(
            "<img src=\"https://tracker.example/p.gif\">",
            "<img SRC='//cdn.example/a.png'>",
            "<td background=http://cdn.example/bg.png>",
            "<div style=\"background: url( 'https://cdn.example/x.png')\">",
            "<link rel=stylesheet href=\"https://cdn.example/a.css\">",
        ).forEach { assertTrue(html("text/html" to EmailBodyValue(it))!!.remoteContent, it) }
        assertFalse(html("text/html" to EmailBodyValue("<img src=\"data:image/png;base64,AA\">"))!!.remoteContent)
    }

    @Test
    fun aCutPartMarksTheBodyTruncated() {
        assertTrue(html("text/html" to EmailBodyValue("<p>Sta", isTruncated = true))!!.truncated)
    }
}
