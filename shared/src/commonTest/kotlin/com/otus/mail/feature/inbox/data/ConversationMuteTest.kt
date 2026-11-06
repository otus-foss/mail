package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.data.EmailChangeQueue
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.emailWriter
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationMuteTest {
    private val login = ADA_LOGIN
    private val store = InMemoryLoginStore().apply { save(login, "secret") }
    private val server = FakeMailServer(MAILBOXES, THREAD)
    private val connections = JmapConnections(JmapClient(stalwartLike(api = server::answer)), store)
    private val cache = InMemoryMailCache()
    private val queue = EmailChangeQueue(connections, cache, store = null)
    private val mute = emailWriter(connections, cache, queue)

    private val row =
        MailMessage(
            "ada/r2",
            MailSender("Grace", "grace@example.org"),
            "Plans",
            "",
            ReceivedAt("2026-10-03", "09:00"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "a", mailboxRole = MailboxRole.Inbox),
            emailId = "r2",
        )

    @Test
    fun mutingMarksEveryEmailOfTheThreadAndUnmutingClearsIt() =
        runTest {
            assertFalse(mute.isMuted(row))
            assertTrue(mute.setMuted(row, muted = true))
            val patched =
                server
                    .calls("Email/set")
                    .single()
                    .getValue("update")
                    .jsonObject
            assertEquals(setOf("r1", "r2", "s1"), patched.keys, "Emails outside the row's folder are muted too")
            assertTrue(patched.values.all { "keywords/${'$'}muted" in it.jsonObject })
            assertTrue(mute.isMuted(row))
            assertTrue(mute.setMuted(row, muted = false))
            assertFalse(mute.isMuted(row))
        }

    @Test
    fun anOfflineMuteWaitsForTheServerAndMarksTheRowsEmails() =
        runTest {
            server.down = true
            assertTrue(mute.setMuted(row, muted = true), "The mute waits for the server")
            server.down = false
            assertFalse(queue.flushAll())
            val patched =
                server
                    .calls("Email/set")
                    .single()
                    .getValue("update")
                    .jsonObject
            assertEquals(setOf("r2"), patched.keys, "Offline, the row's own emails stand for the thread")
        }

    private companion object {
        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"},
            {"id": "s", "name": "Sent", "role": "sent"}"""

        const val THREAD = """
            {"id": "r1", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {},
             "receivedAt": "2026-10-01T09:00:00Z"},
            {"id": "r2", "threadId": "t1", "mailboxIds": {"a": true}, "keywords": {},
             "receivedAt": "2026-10-03T09:00:00Z"},
            {"id": "s1", "threadId": "t1", "mailboxIds": {"s": true}, "keywords": {},
             "receivedAt": "2026-10-04T09:00:00Z"}"""
    }
}
