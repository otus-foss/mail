package com.otus.mail.app

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MessageMarkingTest {
    private val sent = MutableStateFlow<List<String>>(emptyList())
    private val serverMessages = MutableStateFlow<List<MailMessage>>(emptyList())
    private var messageLoads = 0
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
        serverMessages.value = messages.toList()
        val engine =
            stalwartLike { body ->
                sent.update { it + body }
                setReply(body)
            }
        val services = adaServices(engine)
        val folders =
            testFolders(services) {
                folderLoads++
                emptyList()
            }
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope): InboxSnapshot {
                    messageLoads++
                    return InboxSnapshot(serverMessages.value)
                }
            }
        val feed = InboxFeedState(source, this)
        feed.refresh()
        runCurrent()
        return MessageMarking(services, folders, this) to feed
    }

    /** Updates every id the `Email/set` call names, except [refused] ones. */
    private fun setReply(body: String): String {
        val (kept, changed) = UPDATE.findAll(body).map { it.groupValues[1] }.partition { it in refused }
        serverMessages.update { rows ->
            rows.map { if (it.emailId in changed) it.copy(attributes = it.attributes.copy(unread = false)) else it }
        }
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
            assertEquals(1, sent.value.count { "Email/set" in it }, sent.value.toString())
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
            assertEquals(2, messageLoads)
            assertEquals("Couldn't finish changing 1 message. Try again.", feed.notice)
            settle { folderLoads > 0 }
            assertEquals(1, folderLoads, "The marked messages still change the unread counts")
        }

    @Test
    fun aRefusedSingleMarkRefreshesFoldersAndKeepsTheFailureNotice() =
        runTest {
            refused = setOf("m1")
            val (marking, feed) = marking(message("m1"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(emptyList(), feed.unread())
            settle { feed.notice != null }
            settle { false }
            assertEquals(listOf("m1"), feed.unread())
            assertEquals("Couldn't finish changing the message. Try again.", feed.notice)
            assertEquals(1, folderLoads)
            assertEquals(2, messageLoads)
        }

    private companion object {
        val UPDATE = Regex("\"(m\\d)\":\\{\"keywords/")
    }
}
