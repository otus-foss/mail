package com.otusfoss.mail.feature.read.presentation

import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.domain.MailingList
import com.otusfoss.mail.core.messages.presentation.AttachmentKindName
import com.otusfoss.mail.core.messages.presentation.attachmentSummary
import com.otusfoss.mail.core.messages.presentation.detail
import com.otusfoss.mail.core.messages.presentation.drawable
import com.otusfoss.mail.core.messages.presentation.kind
import com.otusfoss.mail.core.messages.presentation.symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentLabelsTest {
    private val pdf = MailAttachment("Q4-plan-v3.pdf", "application/pdf", 240 * 1024L, "b1")
    private val image = MailAttachment("whiteboard.jpg", "image/jpeg", 2_202_010L, "b2")
    private val sheet =
        MailAttachment(
            "numbers.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            1_992_294L,
            "b3",
        )

    @Test
    fun kindReadsTheMediaType() {
        assertEquals(AttachmentKindName.Pdf, pdf.kind())
        assertEquals(AttachmentKindName.Image, image.kind())
        assertEquals(AttachmentKindName.Sheet, sheet.kind())
        assertEquals(AttachmentKindName.Document, MailAttachment("a.docx", "application/msword", 1, "b").kind())
    }

    @Test
    fun kindFallsBackToTheExtension() {
        assertEquals(AttachmentKindName.Pdf, MailAttachment("a.PDF", "application/octet-stream", 1, "b").kind())
        assertEquals(AttachmentKindName.Data, MailAttachment("a.csv", "application/octet-stream", 1, "b").kind())
        assertEquals(AttachmentKindName.Slides, MailAttachment("deck.pptx", "application/octet-stream", 1, "b").kind())
        assertEquals(AttachmentKindName.Other, MailAttachment("README", "application/octet-stream", 1, "b").kind())
    }

    @Test
    fun officeTypesWinOverTheXmlTheyName() {
        val deck = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        assertEquals(AttachmentKindName.Slides, MailAttachment("deck", deck, 1, "b").kind())
        assertEquals(AttachmentKindName.Data, MailAttachment("feed", "application/xml", 1, "b").kind())
        assertEquals(AttachmentKindName.Archive, MailAttachment("a.zip", "application/zip", 1, "b").kind())
        assertEquals(AttachmentKindName.Calendar, MailAttachment("invite.ics", "text/calendar", 1, "b").kind())
    }

    @Test
    fun wrappersAndVectorsWinOverWhatTheyHold() {
        assertEquals(AttachmentKindName.Design, MailAttachment("logo", "image/svg+xml", 1, "b").kind())
        assertEquals(
            AttachmentKindName.Signature,
            MailAttachment("signature.asc", "application/pgp-signature", 1, "b").kind(),
        )
        assertEquals(AttachmentKindName.Encrypted, MailAttachment("msg", "application/pgp-encrypted", 1, "b").kind())
        assertEquals(AttachmentKindName.Ebook, MailAttachment("book", "application/epub+zip", 1, "b").kind())
        assertEquals(
            AttachmentKindName.Location,
            MailAttachment("trip", "application/vnd.google-earth.kml+xml", 1, "b").kind(),
        )
    }

    @Test
    fun armoredFilesFollowTheirMediaType() {
        assertEquals(AttachmentKindName.Key, MailAttachment("publickey.asc", "application/pgp-keys", 1, "b").kind())
        assertEquals(
            AttachmentKindName.Other,
            MailAttachment("encrypted.asc", "application/octet-stream", 1, "b").kind(),
        )
    }

    @Test
    fun wrappersAndAppsWinOverTheirContainerType() {
        assertEquals(
            AttachmentKindName.Encrypted,
            MailAttachment("photo.jpg", "application/pkcs7-mime", 1, "b").kind(),
        )
        assertEquals(AttachmentKindName.App, MailAttachment("otus.apk", "application/zip", 1, "b").kind())
    }

    @Test
    fun archivesAreNotCode() {
        assertEquals(AttachmentKindName.Other, MailAttachment("backup.cpio", "application/x-cpio", 1, "b").kind())
        assertEquals(AttachmentKindName.Code, MailAttachment("main.c", "text/x-c", 1, "b").kind())
    }

    @Test
    fun everyKindHasASymbol() = AttachmentKindName.entries.forEach { assertEquals(it.name, it.symbol.name) }

    @Test
    fun tiffIsNotDrawn() {
        assertTrue(image.drawable())
        assertFalse(MailAttachment("scan.tiff", "image/tiff", 1, "b").drawable())
        assertFalse(pdf.drawable())
    }

    @Test
    fun detailNamesKindAndSize() {
        assertEquals("PDF · 240 KB", pdf.detail())
        assertEquals("Image · 2.1 MB", image.detail())
        assertEquals("Spreadsheet · 1.9 MB", sheet.detail())
    }

    @Test
    fun detailOfAKeptFileSaysItIsOffline() {
        assertEquals("PDF · 240 KB · Offline", pdf.detail(kept = true))
    }

    @Test
    fun detailOfOtherFilesNamesTheExtension() {
        assertEquals("ZIP · 1 MB", MailAttachment("a.zip", "application/zip", 1024 * 1024L, "b").detail())
        assertEquals("File · 12 bytes", MailAttachment("README", "application/octet-stream", 12, "b").detail())
    }

    @Test
    fun summaryCountsFilesAndAddsSizes() {
        assertEquals("3 attachments · 4.2 MB", attachmentSummary(listOf(pdf, image, sheet)))
        assertEquals("1 attachment · 240 KB", attachmentSummary(listOf(pdf)))
    }

    private fun list(perWeek: Double?) = MailingList("Sentry weekly digest", perWeek, emptyList())

    @Test
    fun subtitleSaysHowOftenTheListWrites() {
        assertEquals("Mailing list · about once a week", list(1.0).subtitle())
        assertEquals("Mailing list · a few times a week", list(3.0).subtitle())
        assertEquals("Mailing list · about once a month", list(0.25).subtitle())
        assertEquals("Mailing list · about every day", list(7.0).subtitle())
        assertEquals("Mailing list · now and then", list(0.05).subtitle())
    }

    @Test
    fun subtitleLeavesOutAnUnknownFrequency() = assertEquals("Mailing list", list(null).subtitle())
}
