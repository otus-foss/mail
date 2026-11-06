package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.app.adaServices
import com.otus.mail.app.settle
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessageMarkingTest {
    private val sent = MutableStateFlow<List<String>>(emptyList())
    private val serverMessages = MutableStateFlow<List<MailMessage>>(emptyList())
    private var messageLoads = 0
    private var refused = emptySet<String>()
    private var countChanges = 0
    private val toasts = ToastState()

    private fun message(
        id: String,
        unread: Boolean = true,
    ) = MailMessage(
        id,
        MailSender("Grace", "grace@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Inbox", "Ada", "ada", mailboxId = "i", mailboxRole = MailboxRole.Inbox),
        MailAttributes(unread = unread),
        emailId = id,
    )

    /**
     * Marking for a feed holding [messages], and the feed. The feed and the saves of marks run in
     * [screen]. Each announcement that folder counts changed counts.
     */
    private fun TestScope.marking(
        vararg messages: MailMessage,
        screen: CoroutineScope = this,
    ): Pair<MessageMarking, InboxFeedState> {
        serverMessages.value = messages.toList()
        val engine =
            stalwartLike { body ->
                sent.update { it + body }
                setReply(body)
            }
        val services = adaServices(engine)
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope): InboxSnapshot {
                    messageLoads++
                    return InboxSnapshot(serverMessages.value)
                }
            }
        val feed = InboxFeedState(source, screen)
        val edits = MailEdits(services.writer, screen, MoveToasts(toasts, this))
        edits.observe(feed.edits)
        backgroundScope.launch { edits.changed.collect { countChanges++ } }
        feed.refresh()
        runCurrent()
        return MessageMarking(edits) to feed
    }

    /** Sets or clears `$seen` on every id the `Email/set` call names, except [refused] ones. */
    private fun setReply(body: String): String {
        val patches = UPDATE.findAll(body).associate { it.groupValues[1] to (it.groupValues[2] == "true") }
        val (kept, changed) = patches.keys.partition { it in refused }
        serverMessages.update { rows ->
            rows.map { row ->
                val seen = patches[row.emailId]?.takeIf { row.emailId in changed } ?: return@map row
                row.copy(attributes = row.attributes.copy(unread = !seen))
            }
        }
        val updated = changed.joinToString { "\"$it\": null" }
        val notUpdated = kept.joinToString { "\"$it\": {\"type\": \"forbidden\"}" }
        return """{"methodResponses": [["Email/set", {"newState": "e2", "updated": {$updated},
            "notUpdated": {$notUpdated}}, "c0"]]}"""
    }

    private fun InboxFeedState.unread(): List<String> = messages.filter { it.attributes.unread }.map { it.id }

    /** Each id an `Email/set` [body] patches, with the `$seen` value it sends. */
    private fun patched(body: String): List<Pair<String, String>> =
        UPDATE.findAll(body).map { it.groupValues[1] to it.groupValues[2] }.toList()

    /** Runs the action of the toast that says [text]. */
    private fun tap(text: String) {
        val toast = toasts.messages.single { it.text == text }
        toast.action?.onClick?.invoke()
    }

    @Test
    fun markingManyMessagesSendsOneUpdateAndChangesFolderCountsOnce() =
        runTest {
            val (marking, feed) = marking(message("m1"), message("m2"), message("m3"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(emptyList(), feed.unread(), "Every message shows read at once")
            assertEquals(listOf("3 emails marked as read"), toasts.messages.map { it.text })
            settle { countChanges > 0 }
            // Another refresh would follow here; give it the chance.
            settle { false }
            assertEquals(1, sent.value.count { "Email/set" in it }, sent.value.toString())
            assertEquals(1, countChanges)
            assertEquals(emptyList(), feed.unread())
            assertNull(feed.notice)
        }

    @Test
    fun onlyTheRefusedMessagesGoBackAndRetrySendsOnlyThose() =
        runTest {
            refused = setOf("m2")
            val (marking, feed) = marking(message("m1"), message("m2"), message("m3"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            val failure = "Couldn't mark 1 email as read"
            settle { toasts.messages.any { it.text == failure } }
            assertEquals(listOf("m2"), feed.unread())
            assertEquals(2, messageLoads)
            assertTrue(toasts.messages.single { it.text == failure }.sticky, "Retry waits for the user")
            assertNull(feed.notice)
            settle { countChanges > 0 }
            assertEquals(1, countChanges, "The marked messages still change the unread counts")

            refused = emptySet()
            sent.value = emptyList()
            tap(failure)
            settle { feed.unread().isEmpty() && sent.value.isNotEmpty() }
            settle { false }
            assertEquals(listOf(listOf("m2" to "true")), sent.value.filter { "Email/set" in it }.map(::patched))
            assertEquals(emptyList(), feed.unread())
        }

    @Test
    fun aRefusedSingleMarkGoesBackWithRetry() =
        runTest {
            refused = setOf("m1")
            val (marking, feed) = marking(message("m1"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(emptyList(), feed.unread())
            settle { toasts.messages.any { it.text == "Couldn't mark the email as read" } }
            settle { false }
            assertEquals(listOf("m1"), feed.unread())
            assertEquals(0, countChanges, "Nothing changed, so the folder counts stay")
            assertEquals(2, messageLoads)
        }

    @Test
    fun undoSetsBackOnlyTheMessagesThatChanged() =
        runTest {
            val (marking, feed) = marking(message("m1", unread = false), message("m2"), message("m3"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            assertEquals(listOf("2 emails marked as read"), toasts.messages.map { it.text })
            settle { sent.value.isNotEmpty() }
            sent.value = emptyList()
            tap("2 emails marked as read")
            settle { feed.unread().size == 2 }
            settle { false }
            assertEquals(listOf("m2", "m3"), feed.unread(), "m1 was read before, so it stays read")
            val undo = sent.value.single { "Email/set" in it }
            assertEquals(listOf("m2" to "null", "m3" to "null"), patched(undo))
        }

    @Test
    fun undoStillSetsBackAfterTheScreenThatMarkedIsGone() =
        runTest {
            val screen = CoroutineScope(coroutineContext + Job())
            val (marking, feed) = marking(message("m1"), message("m2"), screen = screen)
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true)
            // The activity is recreated before the save finishes, which cancels it.
            screen.cancel()
            tap("2 emails marked as read")
            settle { sent.value.any { "Email/set" in it } }
            settle { false }
            val undo = sent.value.last { "Email/set" in it }
            assertEquals(listOf("m1" to "null", "m2" to "null"), patched(undo), "Undo sets back every message")
        }

    @Test
    fun openingAMessageMarksItReadWithoutUndo() =
        runTest {
            val (marking, feed) = marking(message("m1"))
            marking.mark(feed, feed.messages, MessageFlag.Read, on = true, undoable = false)
            settle { sent.value.isNotEmpty() }
            assertEquals(emptyList(), toasts.messages)
        }

    private companion object {
        val UPDATE = Regex("\"(m\\d)\":\\{\"keywords/\\\$seen\":(true|null)")
    }
}
