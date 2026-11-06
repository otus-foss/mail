package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Conversation rows as the list loads them. */
class JmapMailSourceThreadsTest {
    private val login = ADA_LOGIN
    private val store = InMemoryLoginStore().apply { save(login, "secret") }
    private val server = FakeMailServer(MAILBOXES, THREAD)
    private val source = server.source(store)

    @Test
    fun theThreadLoadsWithItsRowsAndCountsSentButNotTrash() =
        runTest {
            val message = source.messages(MailScope.Inbox).messages.single()
            val conversation = message.conversation!!
            assertEquals(3, conversation.messageCount)
            assertEquals("ada@example.org", conversation.last.sender.address)
            assertEquals(listOf("r2", "r1"), conversation.members.map { it.emailId })
            assertTrue(message.attributes.unread)
            assertEquals(setOf("a", "s", "t"), message.location.mailboxIds)
            val request = server.bodies.single { "\"preview\"" in it }
            assertTrue("\"path\":\"/list/*/threadId\"" in request, request)
            assertTrue("\"path\":\"/list/*/emailIds\"" in request, request)
        }

    @Test
    fun aRefusedThreadCallLeavesEachRowAlone() =
        runTest {
            server.refused = setOf("Thread/get")
            val conversation =
                source
                    .messages(MailScope.Inbox)
                    .messages
                    .single()
                    .conversation!!
            assertEquals(1, conversation.messageCount)
            assertEquals(listOf("r2"), conversation.members.map { it.emailId })
        }

    @Test
    fun threadsTooLargeForOneGetArriveInSmallerOnesAndStay() =
        runTest {
            val server = FakeMailServer(MAILBOXES, "$THREAD, $SECOND_THREAD").apply { maxIdsInGet = 2 }
            val session = STALWART_SESSION.replace("\"$CORE\": {}", "\"$CORE\": {\"maxObjectsInGet\": 2}")
            val source = server.source(store, session = session)
            val rows = source.messages(MailScope.Inbox).messages.associateBy { it.emailId }
            assertEquals(3, rows.getValue("r2").conversation!!.messageCount)
            assertEquals(2, rows.getValue("o2").conversation!!.messageCount, "The other thread keeps its emails too")
            assertEquals(1, server.fetchedIds().count { it.size > 2 }, "Only the back-reference asks for too many")
            val calls = server.calls.size
            source.messages(MailScope.Inbox)
            val threadReads =
                server.calls
                    .drop(calls)
                    .filter { (name, arguments) -> name == "Thread/get" && !arguments.ids().isNullOrEmpty() }
            assertTrue(threadReads.isEmpty(), "The threads are not read again")
        }

    @Test
    fun aThreadInTwoChosenFoldersIsOneRowUnderItsNewestEmail() =
        runTest {
            val row = source.messages(MailScope.Roles(listOf(MailboxRole.Inbox, MailboxRole.Sent))).messages.single()
            assertEquals("ada/s1", row.id)
            assertEquals("Sent", row.location.mailbox)
            assertEquals(listOf("s1", "r2", "r1"), row.conversation!!.members.map { it.emailId })
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"},
            {"id": "s", "name": "Sent", "role": "sent"}, {"id": "t", "name": "Trash", "role": "trash"}"""

        const val GRACE = """[{"name": "Grace", "email": "grace@example.org"}]"""
        const val ADA = """[{"name": "Ada", "email": "ada@example.org"}]"""

        val THREAD = """
            {"id": "r1", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {"${'$'}seen": true},
             "from": $GRACE, "to": $ADA, "receivedAt": "2026-10-01T09:00:00Z"},
            {"id": "r2", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {},
             "from": $GRACE, "to": $ADA, "subject": "Plans", "receivedAt": "2026-10-03T09:00:00Z"},
            {"id": "s1", "threadId": "t1", "mailboxIds": {"s": true}, "keywords": {"${'$'}seen": true},
             "from": $ADA, "to": $GRACE, "subject": "Re: Plans", "receivedAt": "2026-10-04T09:00:00Z"},
            {"id": "x1", "threadId": "t1", "mailboxIds": {"t": true}, "keywords": {},
             "from": $GRACE, "to": $ADA, "receivedAt": "2026-10-05T09:00:00Z"}"""

        val SECOND_THREAD = """
            {"id": "o1", "threadId": "t2", "mailboxIds": {"a": true}, "keywords": {},
             "from": $GRACE, "to": $ADA, "receivedAt": "2026-09-01T09:00:00Z"},
            {"id": "o2", "threadId": "t2", "mailboxIds": {"a": true}, "keywords": {},
             "from": $GRACE, "to": $ADA, "receivedAt": "2026-09-02T09:00:00Z"}"""

        const val CORE = "urn:ietf:params:jmap:core"
    }
}
