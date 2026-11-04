package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailingList
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(AttachmentKindName.Sheet, MailAttachment("a.csv", "application/octet-stream", 1, "b").kind())
        assertEquals(AttachmentKindName.Slides, MailAttachment("deck.pptx", "application/octet-stream", 1, "b").kind())
        assertEquals(AttachmentKindName.Other, MailAttachment("README", "application/octet-stream", 1, "b").kind())
    }

    @Test
    fun officeTypesWinOverTheXmlTheyName() {
        val deck = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        assertEquals(AttachmentKindName.Slides, MailAttachment("deck", deck, 1, "b").kind())
        assertEquals(AttachmentKindName.Code, MailAttachment("feed", "application/xml", 1, "b").kind())
        assertEquals(AttachmentKindName.Archive, MailAttachment("a.zip", "application/zip", 1, "b").kind())
        assertEquals(AttachmentKindName.Calendar, MailAttachment("invite.ics", "text/calendar", 1, "b").kind())
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
