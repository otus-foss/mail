package com.otus.mail.feature.inbox.data

import com.otus.mail.core.mail.EmailAddress
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.feature.inbox.domain.MailConversation
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

    private fun email(
        id: String,
        day: Int,
        box: String,
        from: EmailAddress = grace,
        to: List<EmailAddress> = listOf(ada),
        cc: List<EmailAddress>? = null,
        keywords: Set<String> = setOf(SEEN_KEYWORD),
        listId: String? = null,
    ) = EmailObject(
        id = id,
        threadId = "t1",
        mailboxIds = mapOf(box to true),
        keywords = keywords.associateWith { true },
        from = listOf(from),
        to = to,
        cc = cc,
        receivedAt = "2026-10-${day.toString().padStart(2, '0')}T09:30:00Z",
        listId = listId,
    )

    private fun conversation(
        row: EmailObject,
        thread: List<EmailObject>,
        mailbox: String = "inbox",
    ): MailConversation =
        row
            .toMessage("ada", "Ada", mailbox, TimeZone.UTC, "ada@example.org", mailbox)
            .inConversation(row, thread, mailbox, view, TimeZone.UTC)
            .conversation!!

    private val question = email("e1", 1, "inbox")
    private val answer = email("e2", 2, "sent", from = ada, to = listOf(grace))

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
        val draft = email("e3", 3, "drafts", from = ada, keywords = setOf(DRAFT_KEYWORD))
        val deleted = email("e4", 4, "trash", from = ada)
        val withDraft = conversation(question, listOf(question, draft, deleted))
        assertFalse(withDraft.awaitingReply)
        assertTrue(withDraft.needsMyReply, "The user is in Grace's To field")
    }

    @Test
    fun needsMyReplyWantsTheUserInvolvedOrDirectlyAddressed() {
        val copied = email("e1", 1, "inbox", to = listOf(team), cc = listOf(ada))
        assertFalse(conversation(copied, listOf(copied)).needsMyReply, "Only copied, and never wrote")
        val direct = email("e1", 1, "inbox", to = listOf(team, ada))
        assertTrue(conversation(direct, listOf(direct)).needsMyReply)
        val earlier = email("e0", 1, "sent", from = ada, to = listOf(team))
        val reply = email("e5", 5, "inbox", from = team, to = listOf(team), cc = listOf(ada))
        assertTrue(conversation(reply, listOf(earlier, reply)).needsMyReply, "The user wrote earlier in the thread")
    }

    @Test
    fun aNewsletterNeverNeedsAReply() {
        val newsletter = email("e1", 1, "inbox", listId = "<news.example.org>")
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
                .toMessage("ada", "Ada", "inbox", TimeZone.UTC, "ada@example.org", "inbox")
                .inConversation(followUp, listOf(question, answer, followUp), "inbox", view, TimeZone.UTC)
        val fromSent =
            answer
                .toMessage("ada", "Ada", "sent", TimeZone.UTC, "ada@example.org", "sent")
                .inConversation(answer, listOf(question, answer, followUp), "sent", view, TimeZone.UTC)
        val merged = listOf(fromSent, fromInbox).mergedByThread().single().conversation!!
        assertFalse(merged.awaitingReply)
        assertTrue(merged.needsMyReply)
    }
}
