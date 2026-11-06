package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.feature.inbox.data.FakeMailServer
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.MailConversation
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MailSource
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.ThreadEmail
import com.otus.mail.feature.inbox.domain.ThreadMember
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.MoveTarget
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
 * lists `e3` and `e1`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadPageChangesTest {
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
        MailLocation("Inbox", "Ada", "ada", "ada@example.org", mailboxId = boxes.first(), mailboxIds = boxes),
        emailId = id,
    )

    /**
     * The changes for a feed that lists [row], made in [composition]; each load of the feed counts.
     * Toasts run their work in the test's scope, as in the process's.
     */
    private fun TestScope.changes(composition: CoroutineScope = this): Pair<ReadPageChanges, InboxFeedState> {
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
        val folders = testFolders(services) { emptyList() }
        return ReadPageChanges(services, folders, MoveToasts(toasts, this), composition, feed) to feed
    }

    private fun updates(): List<JsonObject> = server.calls("Email/set").map { it.getValue("update").jsonObject }

    @Test
    fun foldersChangeEveryEmailOfTheConversationInOneCallWithWholeMailboxSets() =
        runTest {
            val (changes, feed) = changes()
            val before = mapOf("e1" to setOf("a"), "e2" to setOf("ar", "fi"), "e3" to setOf("a"))
            var refused = false
            changes.refile(FolderChange(row, before, added = setOf("cl"), removed = setOf("fi"))) { refused = true }
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
            assertFalse(refused)
            val shown = feed.messages.single()
            assertEquals(setOf("a", "cl"), shown.location.mailboxIds)
            assertEquals(listOf(setOf("a", "cl"), setOf("a", "cl")), shown.conversation?.members?.map { it.mailboxIds })
        }

    @Test
    fun aPageMarkChangesOnlyItsOwnEmailAndReloadsTheCombinedRow() =
        runTest {
            val (changes, feed) = changes()
            val page = email("e1")
            changes.mark(row, page, MessageFlag.Flagged, on = true) { error("refused") }
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
        }

    @Test
    fun deletingAnOlderPageTrashesOnlyItsEmailKeepsTheRowAndUndoPutsItBack() =
        runTest {
            val (changes, feed) = changes()
            var back = 0
            changes.trash(row, email("e1"), TRASH) { back++ }
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
            settle { back > 0 }
            assertEquals(json("""{"e1": {"mailboxIds/tr": null, "mailboxIds/a": true}}"""), updates().last())
            assertEquals(1, back)
            assertTrue(feed.messages.isNotEmpty())
        }

    @Test
    fun undoAfterTheActivityWasRecreatedStillPutsTheEmailBack() =
        runTest {
            // The scope of the composition that deleted the page, which a rotation ends.
            val composition = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
            val (changes, _) = changes(composition)
            changes.trash(row, email("e1"), TRASH) {}
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
        val TRASH = MoveTarget("tr", "Trash", FolderShortcut.Trash)

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
