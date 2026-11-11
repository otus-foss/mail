package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.mail.core.mail.EmailAddress
import com.otusfoss.mail.core.mail.EmailObject
import com.otusfoss.mail.core.mail.Keywords
import com.otusfoss.mail.core.messages.data.ThreadView
import com.otusfoss.mail.core.messages.data.inConversation
import com.otusfoss.mail.core.messages.data.merged
import com.otusfoss.mail.core.messages.data.mergedByThread
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.data.toMessage
import com.otusfoss.mail.core.messages.domain.MailConversation
import com.otusfoss.mail.core.messages.domain.MailLocation
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who wrote a thread's newest email, which Awaiting reply and Needs my reply read. */
class ConversationRepliesTest {
    private val grace = EmailAddress("Grace Hopper", "grace@example.org")
    private val ada = EmailAddress("Ada", "Ada@Example.org")
    private val team = EmailAddress("Team", "team@example.org")
    private val view =
        ThreadView(
            shown = setOf("inbox"),
            trashAndJunk = setOf("trash", "junk"),
            sent = setOf("sent"),
            own = setOf("ada@example.org"),
        )

    /** What sets one email apart from a read email Grace sent to Ada. */
    private inner class EmailFixture {
        var from: EmailAddress = grace
        var to: List<EmailAddress> = listOf(ada)
        var cc: List<EmailAddress>? = null
        var keywords: Set<String> = setOf(Keywords.SEEN)
        var listId: String? = null
    }

    /** An email of thread t1 in mailbox [box], received on October [day]. */
    private fun email(
        id: String,
        day: Int,
        box: String,
        build: EmailFixture.() -> Unit = {},
    ): EmailObject {
        val fixture = EmailFixture().apply(build)
        return EmailObject(
            id = id,
            threadId = "t1",
            mailboxIds = mapOf(box to true),
            keywords = fixture.keywords.associateWith { true },
            from = listOf(fixture.from),
            to = fixture.to,
            cc = fixture.cc,
            receivedAt = "2026-10-${day.toString().padStart(2, '0')}T09:30:00Z",
            listId = fixture.listId,
        )
    }

    private fun conversation(
        row: EmailObject,
        thread: List<EmailObject>,
        mailbox: String = "inbox",
    ): MailConversation =
        row
            .toMessage(MailLocation(mailbox, "Ada", "ada", "ada@example.org", mailboxId = mailbox), TimeZone.UTC)
            .inConversation(row, thread, mailbox, view, TimeZone.UTC)
            .conversation!!

    private val question = email("e1", 1, "inbox")
    private val answer =
        email("e2", 2, "sent") {
            from = ada
            to = listOf(grace)
        }

    @Test
    fun theNewestEmailComesFromAcrossTheThreadSentIncluded() {
        val replied = conversation(question, listOf(question, answer))
        assertEquals(
            "ada@example.org",
            replied.last.sender.address
                .lowercase(),
            "The reply in Sent is the newest",
        )
        assertTrue(replied.awaitingReply)
        assertFalse(replied.needsMyReply)
    }

    @Test
    fun awaitingReplyHoldsOnlyWhileTheUsersEmailIsNewest() {
        val followUp = email("e3", 3, "inbox")
        val answered = conversation(followUp, listOf(question, answer, followUp))
        assertFalse(answered.awaitingReply, "Grace wrote after the user")
        assertTrue(answered.needsMyReply, "The user took part, and Grace wrote last")
        val started = conversation(answer, listOf(answer), mailbox = "sent")
        assertTrue(started.awaitingReply, "A thread only the user wrote waits for a reply")
    }

    @Test
    fun aDraftOrADeletedEmailIsNotTheNewest() {
        val draft =
            email("e3", 3, "drafts") {
                from = ada
                keywords = setOf(Keywords.DRAFT)
            }
        val deleted = email("e4", 4, "trash") { from = ada }
        val withDraft = conversation(question, listOf(question, draft, deleted))
        assertFalse(withDraft.awaitingReply)
        assertTrue(withDraft.needsMyReply, "The user is in Grace's To field")
    }

    @Test
    fun needsMyReplyWantsTheUserInvolvedOrDirectlyAddressed() {
        val copied =
            email("e1", 1, "inbox") {
                to = listOf(team)
                cc = listOf(ada)
            }
        assertFalse(conversation(copied, listOf(copied)).needsMyReply, "Only copied, and never wrote")
        val direct = email("e1", 1, "inbox") { to = listOf(team, ada) }
        assertTrue(conversation(direct, listOf(direct)).needsMyReply)
        val earlier =
            email("e0", 1, "sent") {
                from = ada
                to = listOf(team)
            }
        val reply =
            email("e5", 5, "inbox") {
                from = team
                to = listOf(team)
                cc = listOf(ada)
            }
        assertTrue(conversation(reply, listOf(earlier, reply)).needsMyReply, "The user wrote earlier in the thread")
    }

    @Test
    fun aNewsletterNeverNeedsAReply() {
        val newsletter = email("e1", 1, "inbox") { listId = "<news.example.org>" }
        val conversation = conversation(newsletter, listOf(newsletter))
        assertFalse(conversation.needsMyReply)
        assertFalse(conversation.awaitingReply)
    }

    @Test
    fun conversationsStillNeedBothSides() {
        assertTrue(conversation(question, listOf(question, answer)).exchanged)
        assertFalse(conversation(question, listOf(question)).exchanged)
    }

    @Test
    fun rowsOfOneThreadKeepTheNewerReplyState() {
        val followUp = email("e3", 3, "inbox")
        val fromInbox =
            followUp
                .toMessage(MailLocation("inbox", "Ada", "ada", "ada@example.org", mailboxId = "inbox"), TimeZone.UTC)
                .inConversation(followUp, listOf(question, answer, followUp), "inbox", view, TimeZone.UTC)
        val fromSent =
            answer
                .toMessage(MailLocation("sent", "Ada", "ada", "ada@example.org", mailboxId = "sent"), TimeZone.UTC)
                .inConversation(answer, listOf(question, answer, followUp), "sent", view, TimeZone.UTC)
        val merged = listOf(fromSent, fromInbox).mergedByThread().single().conversation!!
        assertFalse(merged.awaitingReply)
        assertTrue(merged.needsMyReply)
    }

    @Test
    fun aMergedRowTakesItsLastEmailAndReplyStateFromTheSameRow() {
        val inboxRow = email("e2", 3, "inbox")
        val labelRow = email("e1", 2, "label")
        val mine =
            email("e4", 5, "sent") {
                from = ada
                to = listOf(grace)
            }
        val fromInbox =
            inboxRow
                .toMessage(MailLocation("inbox", "Ada", "ada", "ada@example.org", mailboxId = "inbox"), TimeZone.UTC)
                .inConversation(inboxRow, listOf(inboxRow), "inbox", view, TimeZone.UTC)
        val fromLabel =
            labelRow
                .toMessage(MailLocation("label", "Ada", "ada", "ada@example.org", mailboxId = "label"), TimeZone.UTC)
                .inConversation(labelRow, listOf(labelRow, mine), "label", view, TimeZone.UTC)
        val merged = listOf(fromInbox, fromLabel).mergedByThread().single().conversation!!
        assertEquals(fromLabel.conversation!!.last, merged.last, "The newer last email wins over the newer row")
        assertTrue(merged.awaitingReply)
        assertTrue(merged.reply.participated)
    }
}
