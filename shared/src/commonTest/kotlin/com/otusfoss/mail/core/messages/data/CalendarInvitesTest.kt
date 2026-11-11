package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.mail.EmailBodyPart
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.feature.read.data.files
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarInvitesTest {
    private val invite = EmailBodyPart(partId = "2", name = "invite.ics", type = "text/calendar")
    private val pdf = EmailBodyPart(partId = "3", name = "agenda.pdf", type = "application/pdf")
    private val view = ThreadView(shown = setOf("inbox"), trashAndJunk = setOf("trash", "junk"))

    private fun email(
        id: String,
        day: Int,
        vararg parts: EmailBodyPart,
    ) = EmailObject(
        id = id,
        threadId = "t1",
        mailboxIds = mapOf("inbox" to true),
        receivedAt = "2026-10-0${day}T09:30:00Z",
        hasAttachment = parts.isNotEmpty(),
        attachments = parts.toList(),
    )

    private fun EmailObject.message(): MailMessage =
        toMessage(MailLocation("Inbox", "Ada", "ada", mailboxId = "inbox"), TimeZone.UTC)

    private fun row(
        row: EmailObject,
        thread: List<EmailObject>,
    ): MailMessage = row.message().inConversation(row, thread, "inbox", view, TimeZone.UTC)

    @Test
    fun calendarTypesAndFileNamesAreInvites() {
        assertTrue(invite.isInvite())
        assertTrue(EmailBodyPart(type = "TEXT/CALENDAR").isInvite(), "Media types ignore case")
        assertTrue(EmailBodyPart(type = "text/calendar; method=REQUEST").isInvite(), "Parameters do not hide it")
        assertTrue(EmailBodyPart(type = "application/ics", name = "meeting").isInvite())
        assertTrue(EmailBodyPart(type = "application/octet-stream", name = "Meeting.ICS").isInvite())
        assertTrue(EmailBodyPart(name = "busy.ifb").isInvite(), "Free/busy files are iCalendar too")
        assertFalse(pdf.isInvite())
        assertFalse(EmailBodyPart(name = "topics.txt", type = "text/plain").isInvite())
    }

    @Test
    fun anInviteAloneIsNotAFile() {
        val attributes = email("e1", 1, invite).message().attributes
        assertTrue(attributes.invite)
        assertFalse(attributes.hasAttachment, "The row shows the calendar, not the paperclip")
    }

    @Test
    fun anInviteWithAnotherFileIsBoth() {
        val attributes = email("e1", 1, invite, pdf).message().attributes
        assertTrue(attributes.invite)
        assertTrue(attributes.hasAttachment)
    }

    @Test
    fun theServerDecidesWhenItListsNoAttachments() {
        val unlisted = EmailObject(id = "e1", receivedAt = "2026-10-01T09:30:00Z", hasAttachment = true)
        val attributes = unlisted.message().attributes
        assertFalse(attributes.invite)
        assertTrue(attributes.hasAttachment)
    }

    @Test
    fun aConversationMarksInvitesAndFilesOfAnyEmail() {
        val first = email("e1", 1, invite)
        val second = email("e2", 2, pdf)
        val message = row(second, listOf(first, second))
        assertTrue(message.conversation!!.invite, "The older email's invite counts")
        assertFalse(message.attributes.invite, "The row email itself has none")
        assertTrue(message.attributes.hasAttachment)
        val inviteOnly = row(first, listOf(first))
        assertTrue(inviteOnly.conversation!!.invite)
        assertFalse(inviteOnly.attributes.hasAttachment)
    }

    @Test
    fun theRowsOwnCopyWinsOverASlimmerThreadCopy() {
        val first = email("e1", 1, invite)
        val member = first.copy(attachments = null)
        assertTrue(row(first, listOf(member)).conversation!!.invite)
    }

    @Test
    fun mergedRowsKeepAnInvite() {
        val first = email("e1", 1, invite)
        val second = email("e2", 2).copy(mailboxIds = mapOf("archive" to true))
        val inbox = row(first, listOf(first))
        val archive = second.message().inConversation(second, listOf(second), "archive", view, TimeZone.UTC)
        val merged = listOf(archive, inbox).mergedByThread().single()
        assertTrue(merged.conversation!!.invite)
    }
}
