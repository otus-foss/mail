package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.ThreadMember
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationsTest {
    private val grace = EmailAddress("Grace Hopper", "grace@example.org")
    private val ada = EmailAddress("Ada", "ada@example.org")
    private val radia = EmailAddress(null, "Radia@Example.org")
    private val view = ThreadView(shown = setOf("inbox"), trashAndJunk = setOf("trash", "junk"))

    private fun email(
        id: String,
        day: Int,
        box: String,
        from: EmailAddress = grace,
        to: List<EmailAddress> = listOf(ada),
        keywords: Set<String> = setOf(Keywords.SEEN),
        hasAttachment: Boolean = false,
    ) = EmailObject(
        id = id,
        threadId = "t1",
        mailboxIds = mapOf(box to true),
        keywords = keywords.associateWith { true },
        from = listOf(from),
        to = to,
        receivedAt = "2026-10-${day.toString().padStart(2, '0')}T09:30:00Z",
        hasAttachment = hasAttachment,
    )

    private fun row(
        row: EmailObject,
        thread: List<EmailObject>,
        mailbox: String = "inbox",
        rowView: ThreadView = view,
    ): MailMessage =
        row
            .toMessage(MailLocation(mailbox, "Ada", "ada", "ada@example.org", mailboxId = mailbox), TimeZone.UTC)
            .inConversation(row, thread, mailbox, rowView, TimeZone.UTC)

    private val first = email("e1", 1, "inbox", to = listOf(ada)).copy(cc = listOf(radia))
    private val second = email("e2", 3, "inbox", keywords = emptySet(), hasAttachment = true)
    private val reply =
        email("e3", 4, "sent", from = ada, to = listOf(grace), keywords = setOf(Keywords.SEEN, Keywords.ANSWERED))
    private val draft = email("e4", 5, "drafts", from = ada, keywords = setOf(Keywords.DRAFT))
    private val deleted = email("e5", 6, "trash", from = radia, keywords = setOf(Keywords.SEEN, Keywords.FLAGGED))

    @Test
    fun aThreadIsAConversationOnlyWhenBothSidesWrote() {
        val withSent = ThreadView(shown = setOf("inbox"), trashAndJunk = setOf("trash", "junk"), sent = setOf("sent"))
        val both = row(first, listOf(first, reply), rowView = withSent)
        assertTrue(both.conversation!!.exchanged, "Grace wrote and the user answered")
        val onlySent = row(reply, listOf(reply), mailbox = "sent", rowView = withSent)
        assertFalse(onlySent.conversation!!.exchanged, "Nobody answered the user yet")
        val onlyReceived = row(first, listOf(first, second), rowView = withSent)
        assertFalse(onlyReceived.conversation!!.exchanged, "The user never wrote")
    }

    @Test
    fun aRowCountsTheThreadWithoutDraftsAndDeletedMail() {
        val message = row(second, listOf(first, second, reply, draft, deleted))
        val conversation = message.conversation!!
        assertEquals("t1", conversation.threadId)
        assertEquals(3, conversation.messageCount, "Sent counts; the draft and the deleted reply do not")
        assertEquals("ada@example.org", conversation.last.sender.address, "The user's reply dates the row")
        assertEquals("2026-10-04", conversation.last.received.date)
        assertEquals(
            listOf("ada@example.org", "grace@example.org"),
            conversation.senders.map { it.address },
            "Radia was only copied, so she does not count",
        )
        assertTrue(conversation.answered)
        assertEquals(listOf("e2", "e1"), conversation.members.map { it.emailId }, "Only Inbox mail, the row first")
        assertEquals(ThreadMember("e1", setOf("inbox"), "inbox"), conversation.members[1])
    }

    @Test
    fun marksAndMailboxesCoverTheConversation() {
        val message = row(first, listOf(first, second, reply, deleted))
        assertTrue(message.attributes.unread, "An unread email in the Inbox makes the row unread")
        assertFalse(message.attributes.flagged, "A flag in Trash does not count")
        assertTrue(message.attributes.hasAttachment)
        assertTrue(message.attributes.attachments.isEmpty(), "Names stay the row email's own")
        assertEquals(setOf("inbox", "sent", "trash"), message.location.mailboxIds)
        assertEquals("ada/e1", message.id)
    }

    @Test
    fun theTrashListCountsDeletedMail() {
        val trash = ThreadView(shown = setOf("trash"), trashAndJunk = setOf("trash", "junk"))
        val message = row(deleted, listOf(first, deleted), mailbox = "trash", rowView = trash)
        assertEquals(2, message.conversation!!.messageCount)
        assertTrue(message.attributes.flagged)
    }

    @Test
    fun aRowWithoutThreadEmailsStandsForItself() {
        val message = row(first.copy(threadId = ""), emptyList())
        val conversation = message.conversation!!
        assertEquals("e1", conversation.threadId)
        assertEquals(1, conversation.messageCount)
        assertEquals(listOf("e1"), conversation.members.map { it.emailId })
    }

    @Test
    fun rowsOfOneThreadInSeveralMailboxesMerge() {
        val inbox = row(second, listOf(first, second, reply))
        val sent = row(reply, listOf(first, second, reply), mailbox = "sent")
        val other = row(email("o1", 2, "inbox").copy(threadId = "t2"), emptyList())
        val merged = listOf(inbox, other, sent).mergedByThread()
        assertEquals(listOf("ada/e3", "ada/o1"), merged.map { it.id }, "Newest conversation first")
        val thread = merged.first()
        assertEquals("sent", thread.location.mailboxId, "The newer row email stays")
        assertEquals(listOf("e3", "e2", "e1"), thread.conversation!!.members.map { it.emailId })
        assertTrue(thread.attributes.unread, "The Inbox row was unread")
    }

    @Test
    fun aMessageWithoutConversationIsListedOnce() {
        val plain = first.toMessage(MailLocation("Inbox", "Ada", "ada", mailboxId = "inbox"), TimeZone.UTC)
        val merged = listOf(plain, plain.copy(location = plain.location.copy(mailboxId = "label"))).mergedByThread()
        assertEquals("inbox", merged.single().location.mailboxId, "The first stays")
        assertNull(merged.single().conversation)
    }
}
