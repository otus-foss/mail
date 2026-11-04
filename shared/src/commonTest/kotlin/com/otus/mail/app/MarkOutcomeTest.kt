package com.otus.mail.app

import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkOutcomeTest {
    private fun message(
        id: String,
        unread: Boolean = false,
        flagged: Boolean = false,
    ) = MailMessage(
        id,
        MailSender("Grace", "grace@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Inbox", "Ada", "ada", mailboxId = "i"),
        MailAttributes(unread = unread, flagged = flagged),
        emailId = id,
    )

    @Test
    fun undoSetsBackOnlyMessagesTheServerChangedThatHadAnotherMark() {
        val messages = listOf(message("read"), message("unread", unread = true), message("refused", unread = true))
        val outcome = MarkOutcome(messages, marked = setOf("read", "unread"), MessageFlag.Read, on = true)
        val changed = outcome.changed
        assertEquals(listOf("unread"), changed.map { it.id })
        assertEquals(false, changed.single().attributes.unread, "It goes back from the state the mark left it in")
    }

    @Test
    fun undoOfAnUnflagLeavesMessagesThatWereNeverFlagged() {
        val messages = listOf(message("flagged", flagged = true), message("plain"))
        val outcome = MarkOutcome(messages, marked = setOf("flagged", "plain"), MessageFlag.Flagged, on = false)
        assertEquals(listOf("flagged"), outcome.changed.map { it.id })
    }

    @Test
    fun retryTakesOnlyTheRefusedMessagesAsTheyWere() {
        val messages = listOf(message("a", unread = true), message("b", unread = true), message("c", unread = true))
        val outcome = MarkOutcome(messages, marked = setOf("b"), MessageFlag.Read, on = true)
        assertEquals(listOf("a", "c"), outcome.failed.map { it.id })
        assertEquals(listOf(true, true), outcome.failed.map { it.attributes.unread })
    }

    @Test
    fun countsOnlyMessagesWhoseMarkChanges() {
        val messages = listOf(message("a"), message("b", unread = true), message("c", unread = true))
        assertEquals(2, changing(messages, MessageFlag.Read, on = true))
        assertEquals(1, changing(messages, MessageFlag.Read, on = false))
    }

    @Test
    fun namesTheMarkAndHowManyItChanged() {
        assertEquals("Email marked as read", markedText(MessageFlag.Read, on = true, count = 1))
        assertEquals("3 emails marked as unread", markedText(MessageFlag.Read, on = false, count = 3))
        assertEquals("2 emails flagged", markedText(MessageFlag.Flagged, on = true, count = 2))
        assertEquals("Email unflagged", markedText(MessageFlag.Flagged, on = false, count = 1))
    }

    @Test
    fun namesWhatFailedOutOfTheWholeChange() {
        assertEquals(
            "Couldn't mark the email as read",
            notMarkedText(MessageFlag.Read, on = true, failed = 1, total = 1),
        )
        assertEquals(
            "Couldn't mark 1 email as unread",
            notMarkedText(MessageFlag.Read, on = false, failed = 1, total = 4),
        )
        assertEquals("Couldn't flag 3 emails", notMarkedText(MessageFlag.Flagged, on = true, failed = 3, total = 4))
        assertEquals("Couldn't unflag the email", notMarkedText(MessageFlag.Flagged, on = false, failed = 1, total = 1))
    }
}
