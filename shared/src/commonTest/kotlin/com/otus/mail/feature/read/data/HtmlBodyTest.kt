package com.otus.mail.feature.read.data

import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailBodyValue
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MessageHtml
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
            "<style>@import \"https://cdn.example/a.css\";</style>",
            "<style>@import'//cdn.example/a.css';</style>",
            "<svg><image width=\"10\" href=\"https://cdn.example/a.png\"/></svg>",
            "<svg><image xlink:href=\"https://cdn.example/a.png\"/></svg>",
            "<svg><use width=\"9\" href=\"https://cdn.example/icons.svg#i\"/></svg>",
            "<video poster=\"https://cdn.example/p.jpg\"></video>",
            "<object data=\"https://cdn.example/a.svg\"></object>",
        ).forEach { assertTrue(html("text/html" to EmailBodyValue(it))!!.remoteContent, it) }
        assertFalse(html("text/html" to EmailBodyValue("<img src=\"data:image/png;base64,AA\">"))!!.remoteContent)
        assertFalse(html("text/html" to EmailBodyValue("<p data-href=\"https://otus.example\">Hi</p>"))!!.remoteContent)
    }

    @Test
    fun hugeUnclosedSvgTagsAreReadInOnePass() {
        // Each `<use` was searched to the end of the body again, which takes minutes for a large one.
        listOf("<use ", "<image href=").forEach {
            assertFalse(html("text/html" to EmailBodyValue(it.repeat(100_000)))!!.remoteContent, it)
        }
    }

    @Test
    fun aCutPartMarksTheBodyTruncated() {
        assertTrue(html("text/html" to EmailBodyValue("<p>Sta", isTruncated = true))!!.truncated)
    }
}
