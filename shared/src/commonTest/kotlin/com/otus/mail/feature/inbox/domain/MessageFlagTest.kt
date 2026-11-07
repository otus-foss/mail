package com.otus.mail.feature.inbox.domain

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailAttributes
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.hasFlag
import com.otus.mail.core.messages.domain.withFlag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageFlagTest {
    private val message =
        MailMessage(
            "ada/m1",
            MailSender("Grace", "grace@example.org"),
            "Order",
            "",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Ada", mailboxRole = MailboxRole.Inbox),
            MailAttributes(unread = true, attachments = listOf("receipt.pdf")),
        )

    @Test
    fun readIsTheOppositeOfUnread() {
        assertFalse(message.hasFlag(MessageFlag.Read))
        val read = message.withFlag(MessageFlag.Read, on = true)
        assertTrue(read.hasFlag(MessageFlag.Read))
        assertFalse(read.attributes.unread)
    }

    @Test
    fun aMarkChangesNothingElse() {
        val flagged = message.withFlag(MessageFlag.Flagged, on = true)
        assertTrue(flagged.hasFlag(MessageFlag.Flagged))
        assertEquals(message, flagged.withFlag(MessageFlag.Flagged, on = false))
        assertEquals(message.attributes.attachments, flagged.attributes.attachments)
    }
}
