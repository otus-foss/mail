package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.data.toMessage
import com.otusfoss.mail.core.messages.domain.MailLocation
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailDisplayTextTest {
    private val hostile =
        EmailObject(
            id = "e1",
            receivedAt = "2026-10-01T12:00:00Z",
            from = listOf(EmailAddress("\u202Elapyap\nSupport", "\u200Fservice@evil.example")),
            subject = "Your\r\naccount\u0000 is locked",
            preview = "Line one\r\nLine\u2066 two",
            hasAttachment = true,
            attachments = listOf(EmailBodyPart("2", "invoice\u202Efdp.exe"), EmailBodyPart("3", "\u200E")),
        )

    @Test
    fun serverTextReachesTheMessageWithoutHiddenCharacters() {
        val message = hostile.toMessage(MailLocation("Inbox", "Ada", "work"), TimeZone.UTC)

        assertEquals("lapyap Support", message.sender.name)
        assertEquals("service@evil.example", message.sender.address)
        assertEquals("Your account is locked", message.subject)
        assertEquals("Line one\nLine two", message.body)
        assertEquals(listOf("invoicefdp.exe", "Attachment"), message.attributes.attachments)
    }

    @Test
    fun aNameOfOnlyHiddenCharactersFallsBackToTheAddress() {
        val message =
            hostile
                .copy(
                    from = listOf(EmailAddress("\u202E\u200F", "grace@example.org")),
                ).toMessage(MailLocation("Inbox", "Ada", "work"), TimeZone.UTC)

        assertEquals("grace@example.org", message.sender.name)
    }
}
