package com.otus.mail.feature.inbox.data

import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.Signature
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DraftEmailTest {
    private val quote = "On 2026-10-11 12:30, Grace wrote:\n> Noon?\n> <ok>"

    @Test
    fun aPlainDraftHasOnlyATextPartWithTheQuoteAfterTheBody() {
        val email = Draft("a1", body = "Thursday works.", quote = quote).toEmail("dr")
        assertFalse("htmlBody" in email.keys)
        assertEquals("Thursday works.\n\n$quote", email.part("text"))
    }

    @Test
    fun aFormattedDraftAddsAnHtmlPartThatQuotesInABlockquote() {
        val draft = Draft("a1", body = "Thursday works.", html = "<p><b>Thursday</b> works.</p>", quote = quote)
        val email = draft.toEmail("dr")
        val html = email.part("html")
        assertTrue(html.startsWith("<p><b>Thursday</b> works.</p><br>On 2026-10-11 12:30, Grace wrote:<br>"))
        assertTrue(html.endsWith("<blockquote>Noon?<br>&lt;ok&gt;</blockquote>"))
        assertEquals("Thursday works.\n\n$quote", email.part("text"))
    }

    @Test
    fun aLongQuoteBuildsItsHtmlInLinearTime() {
        val lines = 40_000
        val long = (1..lines).joinToString("\n", prefix = "Grace wrote:\n") { "> line $it" }
        val started = TimeSource.Monotonic.markNow()
        val html = Draft("a1", body = "Yes.", html = "<p>Yes.</p>", quote = long).htmlContent().orEmpty()
        assertTrue(started.elapsedNow() < 2.seconds, "Copying the run at each line took ${started.elapsedNow()}")
        assertEquals(1, html.split("<blockquote>").size - 1, "The quoted lines form one blockquote")
        assertTrue(html.endsWith("line $lines</blockquote>"))
    }

    private fun kotlinx.serialization.json.JsonObject.part(id: String): String =
        getValue("bodyValues")
            .jsonObject
            .getValue(id)
            .jsonObject
            .getValue("value")
            .jsonPrimitive.content

    @Test
    fun theSignatureFollowsTheBodyInBothParts() {
        val signature = Signature("<p><b>Alex</b></p>", "Alex")
        val email = Draft("a1", body = "Thanks.", signature = signature, quote = quote).toEmail("dr")
        assertEquals("Thanks.\n\n-- \nAlex\n\n$quote", email.part("text"))
        assertTrue(email.part("html").startsWith("Thanks.<br><div><p><b>Alex</b></p></div><br>On 2026-10-11"))
    }

    @Test
    fun inlineImagesSitBesideTheHtmlInARelatedPart() {
        val html =
            EmailHtml(
                "<p>Hi</p><img src=\"cid:image0@otus.mail\">",
                listOf(InlineImage("image0@otus.mail", "b1", "image/png")),
            )
        val email = Draft("a1", body = "Hi", html = "<p>Hi</p>").toEmail("dr", html)
        assertFalse("textBody" in email.keys || "htmlBody" in email.keys)
        val structure = email.getValue("bodyStructure").jsonObject
        assertEquals("multipart/alternative", structure.getValue("type").jsonPrimitive.content)
        val related = structure.getValue("subParts").jsonArray[1].jsonObject
        assertEquals("multipart/related", related.getValue("type").jsonPrimitive.content)
        val image = related.getValue("subParts").jsonArray[1].jsonObject
        assertEquals("image0@otus.mail", image.getValue("cid").jsonPrimitive.content)
        assertEquals("inline", image.getValue("disposition").jsonPrimitive.content)
    }

    @Test
    fun uploadedFilesFollowTheBodyInAMixedPart() {
        val file = DraftAttachment("f1", "plan.pdf", "application/pdf", 2048, AttachmentBlob("a1", "b9"))
        val email = Draft("a1", body = "See attached.", attachments = listOf(file)).toEmail("dr")
        assertFalse("textBody" in email.keys)
        val structure = email.getValue("bodyStructure").jsonObject
        assertEquals("multipart/mixed", structure.type)
        val (body, part) = structure.getValue("subParts").jsonArray.map { it.jsonObject }
        assertEquals("text/plain", body.type)
        assertEquals("b9", part.getValue("blobId").jsonPrimitive.content)
        assertEquals("plan.pdf", part.getValue("name").jsonPrimitive.content)
        assertEquals("attachment", part.getValue("disposition").jsonPrimitive.content)
    }

    @Test
    fun aFileStillUploadingToTheDraftsAccountWaitsForALaterSave() {
        val moved = DraftAttachment("f1", "plan.pdf", "application/pdf", 2048, AttachmentBlob("a2", "b9"))
        val email = Draft("a1", body = "See attached.", attachments = listOf(moved)).toEmail("dr")
        assertFalse("bodyStructure" in email.keys)
        assertTrue("textBody" in email.keys)
    }

    private val kotlinx.serialization.json.JsonObject.type: String
        get() = getValue("type").jsonPrimitive.content
}
