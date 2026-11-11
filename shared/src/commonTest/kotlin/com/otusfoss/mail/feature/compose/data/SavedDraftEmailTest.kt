package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailBodyValue
import com.otusfoss.mail.core.mail.EmailObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedDraftEmailTest {
    private val text = EmailBodyPart(partId = "t", type = "text/plain")
    private val html = EmailBodyPart(partId = "h", type = "text/html")
    private val logo = EmailBodyPart(blobId = "b-logo", type = "image/png", name = "logo.png", cid = "logo@otus.mail")
    private val file = EmailBodyPart(blobId = "b-file", type = "application/pdf", name = "plan.pdf")

    private fun email(
        plain: String?,
        formatted: String,
    ) = EmailObject(
        id = "e1",
        receivedAt = "2026-11-09T10:00:00Z",
        textBody = listOfNotNull(text.takeIf { plain != null }) + listOfNotNull(html.takeIf { plain == null }),
        htmlBody = listOf(html, logo),
        attachments = listOf(logo, file),
        bodyValues = listOfNotNull(plain?.let { "t" to EmailBodyValue(it) }, "h" to EmailBodyValue(formatted)).toMap(),
    )

    @Test
    fun aLineStartingWithOnKeepsTheWholeBody() {
        val saved =
            email(
                "Thanks\nOn Monday we meet\n\n-- \nAda",
                "Thanks<br>On Monday we meet<br><div class=\"otus-signature\">Ada <img src=\"cid:logo@otus.mail\"></div>",
            ).toDraft("ada", whole = false)
        assertEquals("Thanks\nOn Monday we meet", saved.body)
        assertEquals("Thanks<br>On Monday we meet", saved.html)
    }

    @Test
    fun theSignaturesImageGoesWithItAndOtherFilesStay() {
        val formatted = "Hi<br><div class=\"otus-signature\"><img src=\"cid:logo@otus.mail\"></div>"
        val saved = email("Hi\n\n-- \nAda", formatted).toDraft("ada", whole = false)
        assertEquals(listOf("plan.pdf"), saved.attachments.map { it.name })
        val whole = email("Hi\n\n-- \nAda", formatted).toDraft("ada", whole = true)
        assertEquals(listOf("logo.png" to true, "plan.pdf" to false), whole.attachments.map { it.name to it.inline })
    }

    @Test
    fun anotherAppsHtmlOnlyDraftKeepsItsHtmlAndReadsItAsText() {
        val saved = email(null, "<p>Hello <b>Ada</b></p><br><div>Grace</div>").toDraft("ada", whole = false)
        assertEquals("Hello Ada\n\nGrace", saved.body)
        assertEquals("<p>Hello <b>Ada</b></p><br><div>Grace</div>", saved.html)
    }
}
