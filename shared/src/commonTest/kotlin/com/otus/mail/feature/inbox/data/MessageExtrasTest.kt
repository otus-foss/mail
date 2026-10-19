package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailBodyPart
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.Unsubscribe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageExtrasTest {
    private val logo = EmailBodyPart("2", "logo.png", "image/png", blobId = "b-logo", size = 900, cid = "Logo@x")
    private val report =
        EmailBodyPart(
            "3",
            "report.pdf",
            "application/pdf",
            blobId = "b-report",
            size = 240_000,
            disposition = "attachment",
        )
    private val email = EmailObject("e1", receivedAt = "2026-10-04T09:30:00Z", attachments = listOf(logo, report))

    @Test
    fun imagesTheHtmlShowsByCidAreNotListedAsFiles() {
        val files = email.files("""<p>Hi</p><img src="cid:logo@x">""")
        assertEquals(listOf(MailAttachment("report.pdf", "application/pdf", 240_000, "b-report")), files)
        assertEquals(listOf(logo), email.inlineParts("""<img src="CID:logo@x">"""), "Content-Ids match in any case")
    }

    @Test
    fun anImageTheHtmlDoesNotShowStaysAFile() {
        assertEquals(listOf("logo.png", "report.pdf"), email.files("<p>No images</p>").map { it.name })
        assertEquals(emptyList(), email.inlineParts(null))
    }

    @Test
    fun oneClickNeedsThePostHeaderAndAnHttpsAddress() {
        val urls = listOf("<ignored>", "mailto:leave@lists.example.org?subject=bye", "https://lists.example.org/u/1")
        val plain = email.copy(listUnsubscribe = urls)
        assertEquals(
            Unsubscribe(web = "https://lists.example.org/u/1", mail = "mailto:leave@lists.example.org?subject=bye"),
            plain.unsubscribe(),
        )
        val oneClick = plain.copy(listUnsubscribePost = "List-Unsubscribe=One-Click").unsubscribe()
        assertEquals("https://lists.example.org/u/1", oneClick?.oneClick)
        val insecure =
            email
                .copy(
                    listUnsubscribe = listOf("http://lists.example.org/u/1"),
                    listUnsubscribePost = "List-Unsubscribe=One-Click",
                ).unsubscribe()
        assertEquals(Unsubscribe(web = "http://lists.example.org/u/1"), insecure, "Plain http is only opened")
        assertNull(email.unsubscribe(), "Mail without the header cannot be left")
    }

    @Test
    fun theAddressesTheMailWasDeliveredToComeAlong() {
        val delivered =
            email.copy(
                listUnsubscribe = listOf("mailto:leave@lists.example.org"),
                deliveredTo = listOf(listOf(EmailAddress(email = "ada@alias.example.org")), null),
                originalTo = listOf(listOf(EmailAddress(email = "ada@alias.example.org"), EmailAddress(email = " "))),
            )
        assertEquals(listOf("ada@alias.example.org"), delivered.unsubscribe()?.deliveredTo)
    }

    @Test
    fun theListIdNamesTheList() {
        assertEquals("Sentry weekly digest", listName("\"Sentry weekly digest\" <weekly.sentry.io>"))
        assertEquals("weekly.sentry.io", listIdentifier("\"Sentry weekly digest\" <weekly.sentry.io>"))
        assertNull(listName("<weekly.sentry.io>"))
        assertNull(listIdentifier("no brackets"))
    }

    @Test
    fun mailtoAddressesReadTheirFields() {
        assertEquals(
            Mailto(listOf("leave@lists.example.org"), "Please remove me", null),
            parseMailto("mailto:leave@lists.example.org?subject=Please%20remove%20me"),
        )
        assertNull(parseMailto("https://lists.example.org"))
        assertNull(parseMailto("mailto:?subject=nobody"))
    }

    @Test
    fun frequencyCountsTheGapsOverTheWeeksSpanned() {
        val weekly = listOf("2026-10-15T08:00:00Z", "2026-10-08T08:00:00Z", "2026-10-01T08:00:00Z")
        assertEquals(1.0, perWeek(weekly))
        assertNull(perWeek(weekly.take(2)), "Two mails are too few to tell")
    }
}
