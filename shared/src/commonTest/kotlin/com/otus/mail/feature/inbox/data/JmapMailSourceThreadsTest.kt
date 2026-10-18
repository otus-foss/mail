package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.MailScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Conversation rows as the list loads them. */
class JmapMailSourceThreadsTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val server = FakeMailServer(MAILBOXES, THREAD)
    private val source = server.source(InMemoryLoginStore().apply { save(login, "secret") })

    @Test
    fun theThreadLoadsWithItsRowsAndCountsSentButNotTrash() =
        runTest {
            val snapshot = source.messages(MailScope.Inbox)
            val message = snapshot.messages.single()
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
    fun aThreadInTwoChosenFoldersIsOneRowUnderItsNewestEmail() =
        runTest {
            val messages = source.messages(MailScope.Roles(mapOf("inbox" to "Inbox", "sent" to "Sent"))).messages
            val row = messages.single()
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
    }
}
