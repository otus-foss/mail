package com.otus.mail.app

import com.otus.jmap.JmapClient
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MessageMarkingTest {
    private val sent = mutableListOf<String>()
    private var refused = emptySet<String>()
    private var folderLoads = 0

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Grace", "grace@example.org"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-01", "12:00"),
            MailLocation("Inbox", "Ada", "ada", mailboxId = "i"),
            MailAttributes(unread = true),
            emailId = id,
        )

    /** Marking for a feed holding [messages], and the feed. Each folder refresh counts a load. */
    private fun TestScope.marking(vararg messages: MailMessage): Pair<MessageMarking, InboxFeedState> {
        val store = InMemoryLoginStore().apply { save(JmapLogin("ada", URL, "ada@example.org", "Ada"), "secret") }
        val engine =
            stalwartLike { body ->
                sent += body
                setReply(body)
            }
        val services = MailServices(logins = store, createClient = { JmapClient(engine) })
        val folders =
            testFolders(services) {
                folderLoads++
                emptyList()
            }
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope) = InboxSnapshot(messages.toList())
            }
        val feed = InboxFeedState(source, this)
        feed.refresh()
        runCurrent()
        return MessageMarking(services, folders, this) to feed
    }

    /** Updates every id the `Email/set` call names, except [refused] ones. */
    private fun setReply(body: String): String {
        val (kept, changed) = UPDATE.findAll(body).map { it.groupValues[1] }.partition { it in refused }
        val updated = changed.joinToString { "\"$it\": null" }
        val notUpdated = kept.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        return """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {$updated},
            "notUpdated": {$notUpdated}}, "c0"]]}"""
    }

    private fun InboxFeedState.unread(): List<String> = messages.filter { it.attributes.unread }.map { it.id }

    @Test
    fun markingManyMessagesSendsOneUpdateAndRefreshesFoldersOnce() =
        runTest {
            val (marking, feed) = marking(message("m1"), message("m2"), message("m3"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(emptyList(), feed.unread(), "Every message shows read at once")
            settle { folderLoads > 0 }
            // Another refresh would follow here; give it the chance.
            settle { false }
            assertEquals(1, sent.count { "Email/set" in it }, sent.toString())
            assertEquals(1, folderLoads)
            assertEquals(emptyList(), feed.unread())
            assertNull(feed.notice)
        }

    @Test
    fun onlyTheRefusedMessagesGoBackWithANotice() =
        runTest {
            refused = setOf("m2")
            val (marking, feed) = marking(message("m1"), message("m2"), message("m3"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            settle { feed.notice != null }
            assertEquals(listOf("m2"), feed.unread())
            assertEquals("Couldn't change 1 message. Try again.", feed.notice)
            settle { folderLoads > 0 }
            assertEquals(1, folderLoads, "The marked messages still change the unread counts")
        }

    @Test
    fun aRefusedSingleMarkGoesBackWithoutRefreshingFolders() =
        runTest {
            refused = setOf("m1")
            val (marking, feed) = marking(message("m1"))
            // A read mark, because only read marks refresh the folder counts when they are saved.
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(emptyList(), feed.unread())
            settle { feed.notice != null }
            settle { false }
            assertEquals(listOf("m1"), feed.unread())
            assertEquals("Couldn't change the message. Try again.", feed.notice)
            assertEquals(0, folderLoads)
        }

    /** Lets the JMAP calls, which read logins off the test thread, finish until [done], or for a short while. */
    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(SETTLE_STEPS) {
            runCurrent()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private companion object {
        const val URL = "https://mail.example.org/.well-known/jmap"
        const val SETTLE_STEPS = 50
        val UPDATE = Regex("\"(m\\d)\":\\{\"keywords/")
    }
}
