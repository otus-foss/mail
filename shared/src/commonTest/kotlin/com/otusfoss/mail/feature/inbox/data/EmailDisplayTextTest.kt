package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.data.toMessage
import com.otusfoss.mail.core.messages.data.toSender
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.isUsableAddress
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
        assertEquals("\\u{200F}service@evil.example", message.sender.address)
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

    @Test
    fun hiddenCharactersDoNotTurnOneAddressIntoAnother() {
        mapOf(
            "ada@corp\u200E.example.com" to "ada@corp\\u{200E}.example.com",
            "ada@corp\u007F.example.com" to "ada@corp\\u{007F}.example.com",
            "ada@corp.example\u0001.com" to "ada@corp.example\\u{0001}.com",
        ).forEach { (raw, shown) ->
            val sender = EmailAddress("Ada", raw).toSender("a")
            assertEquals(shown, sender.address)
            assertFalse(sender.address.isUsableAddress())
        }
    }

    @Test
    fun aCleanAddressIsKeptAndTheNameIsStillCleaned() {
        val sender = EmailAddress("\u202EAda\u200E Lovelace", " Ada@corp.example.com ").toSender("a")

        assertEquals("Ada Lovelace", sender.name)
        assertEquals("Ada@corp.example.com", sender.address)
    }
}
