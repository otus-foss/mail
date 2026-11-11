package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.Signature
import com.otusfoss.mail.testing.reopened
import com.otusfoss.mail.testing.savedEmail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A copy Write saved splits back into what the user wrote, the signature it had, and the quote. */
class SavedSignatureEmailTest {
    private val me = MailSender("Me", "me@example.com", "a1")
    private val draft = Draft("a1", from = me, body = "Hi", emailId = "d1")
    private val forward =
        listOf(
            "---------- Forwarded message ----------",
            "From: Ada <ada@example.com>",
            "Date: 2026-11-09 10:00",
            "Subject: Plans",
            "To: Me <me@example.com>",
            "",
            "See you then",
            "-- ",
            "Ada",
        ).joinToString("\n")

    @Test
    fun aPlainDraftKeepsItsSignature() {
        val reopened = draft.copy(signature = Signature(text = "Work")).reopened()
        assertEquals("Hi", reopened.body)
        assertEquals("Work", reopened.signature.text)
    }

    @Test
    fun aFormattedDraftKeepsItsHtmlSignature() {
        val signed = draft.copy(html = "<b>Hi</b>", signature = Signature("<i>Work</i>", "Work")).reopened()
        assertEquals("<b>Hi</b>", signed.html)
        assertEquals(Signature("<i>Work</i>", "Work"), signed.signature)
    }

    @Test
    fun aTextOnlySignatureGoesIntoTheHtmlAndComesBack() {
        val signed = draft.copy(html = "<b>Hi</b>", signature = Signature(text = "Alex & co\nWork"))
        assertTrue("<div class=\"otus-signature\">Alex &amp; co<br>Work</div>" in signed.htmlContent().orEmpty())
        val reopened = signed.reopened()
        assertEquals("<b>Hi</b>", reopened.html)
        assertEquals("Alex & co\nWork", reopened.signature.text)
        assertNull(draft.copy(signature = Signature(text = "Work")).htmlContent(), "Plain mail stays plain")
    }

    @Test
    fun aReplyDraftKeepsItsSignatureAndQuote() {
        val reply = draft.copy(signature = Signature(text = "Work"), quote = "On Monday, Ada wrote:\n> Noon?")
        val reopened = reply.reopened()
        assertEquals("Hi", reopened.body)
        assertEquals("Work", reopened.signature.text)
        assertEquals("On Monday, Ada wrote:\n> Noon?", reopened.quote)
    }

    @Test
    fun aBodyLineLikeAQuoteIntroStaysBody() {
        val reopened =
            draft
                .copy(
                    body = "Hi\n\nOn Monday we wrote:\nthe plan",
                    signature = Signature(text = "Work"),
                ).reopened()
        assertEquals("Hi\n\nOn Monday we wrote:\nthe plan", reopened.body)
        assertEquals("Work", reopened.signature.text)
        assertEquals("", reopened.quote)
    }

    @Test
    fun anUnsignedForwardOfASignedMessageStaysUnsignedAndKeepsTheForward() {
        val plain = draft.copy(quote = forward).reopened()
        assertTrue(plain.signature.isEmpty)
        assertEquals("Hi", plain.body)
        assertEquals(forward, plain.quote)
        val formatted = draft.copy(html = "<b>Hi</b>", quote = forward).reopened()
        assertTrue(formatted.signature.isEmpty)
        assertEquals("<b>Hi</b>", formatted.html)
        assertEquals(forward, formatted.quote)
    }

    @Test
    fun anOlderCopyWithUnmarkedHtmlLoadsWhole() {
        val old = draft.savedEmail("Hi\n\n-- \nWork", "<b>Hi</b>").toDraft("a1", whole = false)
        assertEquals("<b>Hi</b>", old.html)
        assertTrue(old.signature.isEmpty, "Its HTML cannot be split, so nothing of it is cut")
    }
}
