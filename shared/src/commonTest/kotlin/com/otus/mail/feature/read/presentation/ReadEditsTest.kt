package com.otus.mail.feature.read.presentation

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.app.adaServices
import com.otus.mail.app.settle
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.FolderChange
import com.otus.mail.core.messages.domain.MailConversation
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.messages.domain.ThreadEmail
import com.otus.mail.core.messages.domain.ThreadMember
import com.otus.mail.core.messages.presentation.MailEdits
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.core.messages.presentation.MoveToasts
import com.otus.mail.feature.inbox.data.FakeMailServer
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Read view's changes to single emails of a conversation: the conversation `t` has `e1` in
 * the inbox, `e2` in Archive and Finance / Invoices, and `e3`, the newest, in the inbox. Its row
 * lists `e3` and `e1`, and the open Read view shows both pages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadEditsTest {
    private val server = FakeMailServer(MAILBOXES, THREAD)
    private val toasts = ToastState()
    private var loads = 0

    private val row =
        email("e3").copy(
            conversation =
                MailConversation(
                    "t",
                    messageCount = 3,
                    last = ThreadEmail(GRACE, emptyList(), ReceivedAt("2026-10-03", "09:00")),
                    senders = listOf(GRACE),
                    answered = false,
                    members = listOf(ThreadMember("e3", setOf("a"), "a"), ThreadMember("e1", setOf("a"), "a")),
                ),
        )

    private fun email(
        id: String,
        boxes: Set<String> = setOf("a"),
    ) = MailMessage(
        "ada/$id",
        GRACE,
        "Plans",
        "",
        ReceivedAt("2026-10-01", "09:00"),
        MailLocation(
            "Inbox",
            "Ada",
            "ada",
            "ada@example.org",
            mailboxId = boxes.first(),
            mailboxIds = boxes,
            mailboxRole = MailboxRole.Inbox,
        ),
        emailId = id,
    )

    /** The Read view of [row], showing its listed emails and every edit. */
    private val state = ReadState(row).apply { showPages(listOf(email("e3"), email("e1"))) }

    /**
     * The changes for a feed that lists [row], made in [composition]; each load of the feed counts.
     * Toasts run their work in the test's scope, as in the process's.
     */
    private fun TestScope.changes(composition: CoroutineScope = this): Pair<ReadEdits, InboxFeedState> {
        val services = adaServices(stalwartLike(api = server::answer))
        val source =
            object : MailSource {
                override suspend fun messages(scope: MailScope): InboxSnapshot {
                    loads++
                    return InboxSnapshot(listOf(row))
                }
            }
        val feed = InboxFeedState(source, this)
        feed.refresh()
        runCurrent()
        val edits = MailEdits(services.writer, composition, MoveToasts(toasts, this))
        edits.observe(feed.edits)
        val changes = ReadEdits(edits, feed)
        changes.observe(state)
        return changes to feed
    }

    private fun pageIds(): List<String> = state.pages.map { it.emailId }

    private fun updates(): List<JsonObject> = server.calls("Email/set").map { it.getValue("update").jsonObject }

    @Test
    fun foldersChangeEveryEmailOfTheConversationInOneCallWithWholeMailboxSets() =
        runTest {
            val (changes, feed) = changes()
            val before = mapOf("e1" to setOf("a"), "e2" to setOf("ar", "fi"), "e3" to setOf("a"))
            changes.refile(FolderChange(row, before, added = setOf("cl"), removed = setOf("fi")))
            assertEquals(listOf(setOf("a", "cl"), setOf("a", "cl")), state.pages.map { it.location.mailboxIds })
            settle { updates().isNotEmpty() }
            assertEquals(
                listOf(
                    json(
                        """{"e1": {"mailboxIds": {"a": true, "cl": true}},
                        "e2": {"mailboxIds": {"ar": true, "cl": true}},
                        "e3": {"mailboxIds": {"a": true, "cl": true}}}""",
                    ),
                ),
                updates(),
            )
            settle { false }
            assertEquals(listOf(setOf("a", "cl"), setOf("a", "cl")), state.pages.map { it.location.mailboxIds })
            val shown = feed.messages.single()
            assertEquals(setOf("a", "cl"), shown.location.mailboxIds)
            assertEquals(listOf(setOf("a", "cl"), setOf("a", "cl")), shown.conversation?.members?.map { it.mailboxIds })
        }

    @Test
    fun aPageMarkChangesOnlyItsOwnEmailAndReloadsTheCombinedRow() =
        runTest {
            val (changes, feed) = changes()
            val page = email("e1")
            changes.mark(row, page, MessageFlag.Flagged, on = true)
            assertEquals(listOf(false, true), state.pages.map { it.attributes.flagged }, "Only its page shows it")
            settle { updates().isNotEmpty() }
            assertEquals(listOf(json("""{"e1": {"keywords/${'$'}flagged": true}}""")), updates())
            assertFalse(
                feed.messages
                    .single()
                    .attributes.flagged,
                "The row waits for the reload",
            )
            settle { loads > 1 }
            assertEquals(2, loads)
            assertEquals(listOf(false, true), state.pages.map { it.attributes.flagged }, "The saved mark stays")
        }

    @Test
    fun deletingAnOlderPageTrashesOnlyItsEmailKeepsTheRowAndUndoPutsItBack() =
        runTest {
            val (changes, feed) = changes()
            changes.trash(row, email("e1"), TRASH)
            assertEquals(listOf("e3"), pageIds(), "The page leaves at once")
            assertEquals(
                listOf("e3"),
                feed.messages
                    .single()
                    .conversation
                    ?.members
                    ?.map { it.emailId },
            )
            assertEquals(
                2,
                feed.messages
                    .single()
                    .conversation
                    ?.messageCount,
            )
            settle { updates().isNotEmpty() }
            assertEquals(listOf(json("""{"e1": {"mailboxIds/a": null, "mailboxIds/tr": true}}""")), updates())
            assertEquals("Email deleted", toasts.messages.single().text)
            toasts.messages
                .single()
                .action
                ?.onClick
                ?.invoke()
            settle { pageIds().size > 1 }
            assertEquals(json("""{"e1": {"mailboxIds/tr": null, "mailboxIds/a": true}}"""), updates().last())
            assertEquals(listOf("e3", "e1"), pageIds(), "Undo puts the page back where it was")
            assertTrue(feed.messages.isNotEmpty())
        }

    @Test
    fun undoAfterTheActivityWasRecreatedStillPutsTheEmailBack() =
        runTest {
            // The scope of the composition that deleted the page, which a rotation ends.
            val composition = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
            val (changes, _) = changes(composition)
            changes.trash(row, email("e1"), TRASH)
            settle { false }
            composition.cancel()
            toasts.messages
                .single()
                .action
                ?.onClick
                ?.invoke()
            settle { updates().size > 1 }
            assertEquals(json("""{"e1": {"mailboxIds/tr": null, "mailboxIds/a": true}}"""), updates().last())
        }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private companion object {
        val GRACE = MailSender("Grace", "grace@example.org")
        val TRASH = MoveTarget("tr", listOf("Trash"), MailboxRole.Trash)

        const val MAILBOXES = """{"id": "a", "name": "Inbox", "role": "inbox"},
            {"id": "ar", "name": "Archive", "role": "archive"}, {"id": "fi", "name": "Invoices"},
            {"id": "cl", "name": "Clients"}, {"id": "tr", "name": "Trash", "role": "trash"}"""

        const val THREAD = """
            {"id": "e1", "threadId": "t", "mailboxIds": {"a": true}, "keywords": {},
             "receivedAt": "2026-10-01T09:00:00Z"},
            {"id": "e2", "threadId": "t", "mailboxIds": {"ar": true, "fi": true}, "keywords": {},
             "receivedAt": "2026-10-02T09:00:00Z"},
            {"id": "e3", "threadId": "t", "mailboxIds": {"a": true}, "keywords": {},
             "receivedAt": "2026-10-03T09:00:00Z"}"""
    }
}
