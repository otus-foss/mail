package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.CacheKind
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mail.EmailObject
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.ThreadObject
import com.otus.mail.core.mail.putObject
import com.otus.mail.core.mail.replaceMailboxes
import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapConversationMessagesTest {
    private val login = ADA_LOGIN
    private val store = InMemoryLoginStore().apply { save(login, "secret") }
    private val server = FakeMailServer(MAILBOXES, THREAD)
    private val cache = InMemoryMailCache()
    private val pages =
        JmapConversationMessages(
            JmapConnections(JmapClient(stalwartLike(api = server::answer)), store),
            store,
            cache,
        ) { TimeZone.UTC }

    private val sender = MailSender("Grace", "grace@example.org", "ada")
    private val row =
        MailMessage(
            "ada/r2",
            sender,
            "Plans",
            "",
            ReceivedAt("2026-10-03", "09:00"),
            MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = "a", mailboxIds = setOf("a", "s")),
            emailId = "r2",
            conversation =
                MailConversation(
                    threadId = "t1",
                    messageCount = 3,
                    last = ThreadEmail(sender, emptyList(), ReceivedAt("2026-10-04", "09:00")),
                    senders = listOf(sender),
                    answered = false,
                    members = emptyList(),
                ),
        )

    @Test
    fun everyMessageOfTheThreadIsAPageNewestFirstWithoutDrafts() =
        runTest {
            cache.write("ada") {
                replaceMailboxes(
                    listOf(MailboxObject("s", "Sent", role = "sent"), MailboxObject("x", "Trash", role = "trash")),
                )
            }
            val messages = pages.messages(row)!!
            assertEquals(
                listOf("ada/s1", "ada/r2", "ada/r1"),
                messages.map { it.id },
                "Drafts and deleted mail stay out",
            )
            assertTrue(messages.all { it.conversation == null }, "An action on a page changes only its email")
            assertEquals("Re: Plans", messages.first().subject)
            assertEquals("s" to "Sent", messages.first().location.mailboxId to messages.first().location.mailbox)
            assertEquals("a" to "Inbox", messages[1].location.mailboxId to messages[1].location.mailbox)
            assertEquals(setOf("s"), messages.first().location.mailboxIds)
            assertEquals("ada@example.org", messages.first().location.accountAddress)
            assertTrue("\"Thread/get\"" in server.bodies.single(), "The thread loads in one request")
        }

    @Test
    fun offlineTheListsSavedThreadAnswersAndAnUnknownOneGivesNull() =
        runTest {
            server.down = true
            assertNull(pages.messages(row), "Nothing was saved for this thread")
            cache.write("ada") {
                putObject(CacheKind.Thread, "t1", ThreadObject.serializer(), ThreadObject("t1", listOf("r1", "r2")))
                listOf("r1" to "2026-10-01T09:00:00Z", "r2" to "2026-10-03T09:00:00Z").forEach { (id, at) ->
                    val email = EmailObject(id, threadId = "t1", mailboxIds = mapOf("a" to true), receivedAt = at)
                    putObject(CacheKind.Email, id, EmailObject.serializer(), email)
                }
            }
            assertEquals(listOf("ada/r2", "ada/r1"), pages.messages(row)?.map { it.id })
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"},
            {"id": "s", "name": "Sent", "role": "sent"}, {"id": "d", "name": "Drafts", "role": "drafts"},
            {"id": "x", "name": "Trash", "role": "trash"}"""

        val THREAD = """
            {"id": "r1", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {},
             "from": [{"name": "Grace", "email": "grace@example.org"}], "receivedAt": "2026-10-01T09:00:00Z"},
            {"id": "r2", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {},
             "from": [{"name": "Grace", "email": "grace@example.org"}], "subject": "Plans",
             "receivedAt": "2026-10-03T09:00:00Z"},
            {"id": "s1", "threadId": "t1", "mailboxIds": {"s": true}, "keywords": {},
             "from": [{"name": "Ada", "email": "ada@example.org"}], "subject": "Re: Plans",
             "receivedAt": "2026-10-04T09:00:00Z"},
            {"id": "d1", "threadId": "t1", "mailboxIds": {"d": true}, "keywords": {"${'$'}draft": true},
             "from": [{"name": "Ada", "email": "ada@example.org"}], "receivedAt": "2026-10-05T09:00:00Z"},
            {"id": "x1", "threadId": "t1", "mailboxIds": {"x": true}, "keywords": {},
             "from": [{"name": "Grace", "email": "grace@example.org"}], "receivedAt": "2026-10-02T09:00:00Z"}"""
    }
}
