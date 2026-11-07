package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.InboxSnapshot
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedStateTest {
    @Test
    fun storageFailurePreservesMessagesAndRetryClearsTheError() =
        runTest {
            val message =
                MailMessage(
                    "a",
                    MailSender("Ada", "ada@example.org"),
                    "Subject",
                    "Body",
                    ReceivedAt("2026-10-01", "12:00"),
                    MailLocation("Inbox", "Ada", mailboxRole = MailboxRole.Inbox),
                )
            var fail = false
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        if (fail) throw InboxStorageException(IllegalStateException("read failed"))
                        return InboxSnapshot(listOf(message))
                    }
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            fail = true
            state.refresh()
            runCurrent()
            assertEquals(listOf(message), state.messages)
            assertNotNull(state.notice)
            assertFalse(state.loading)
            fail = false
            state.refresh()
            runCurrent()
            assertNull(state.notice)
            assertFalse(state.loading)
        }

    @Test
    fun aShownScopeReplacesThePagesMailUntilItIsDropped() =
        runTest {
            val requested = mutableListOf<MailScope>()
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        requested += scope
                        return InboxSnapshot(listOf(message(if (scope == MailScope.Inbox) "inbox" else "later")))
                    }
                }
            val later = MailScope.Folders(setOf(MailScope.Folder("ada", "l")))
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            state.show(later)
            assertEquals(emptyList(), state.messages, "The inbox's mail leaves before the folder loads")
            runCurrent()
            assertEquals(listOf("later"), state.messages.map { it.id })
            state.show(later)
            state.refresh()
            runCurrent()
            state.show(null)
            runCurrent()
            assertEquals(listOf("inbox"), state.messages.map { it.id })
            assertEquals(listOf(MailScope.Inbox, later, later, MailScope.Inbox), requested)
        }

    private fun message(id: String) =
        MailMessage(
            id,
            MailSender("Ada", "ada@example.org"),
            "Subject",
            "Body",
            ReceivedAt("2026-10-01", "12:00"),
            MailLocation("Inbox", "Ada", mailboxRole = MailboxRole.Inbox),
        )

    @Test
    fun refreshCancelsThePreviousRequestWithoutReportingAnError() =
        runTest {
            var calls = 0
            var cancelled = false
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        if (++calls == 1) {
                            try {
                                awaitCancellation()
                            } finally {
                                cancelled = true
                            }
                        }
                        return InboxSnapshot(emptyList())
                    }
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            state.refresh()
            runCurrent()
            assertEquals(true, cancelled)
            assertNull(state.notice)
            assertFalse(state.loading)
        }

    @Test
    fun showLoadsAnotherScopeOnceAndNullReturnsToThePageScope() =
        runTest {
            val loaded = mutableListOf<MailScope>()
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot {
                        loaded += scope
                        return InboxSnapshot(emptyList())
                    }
                }
            val state = InboxFeedState(source, this)
            val archive = MailScope.Mailboxes(setOf(MailboxScope(null, "Archive")))
            state.show(null)
            state.show(archive)
            state.show(archive)
            runCurrent()
            state.show(null)
            runCurrent()
            assertEquals(listOf(archive, MailScope.Inbox), loaded)
        }

    @Test
    fun anOldRequestCannotPublishMessagesOrEndTheNewRequestsLoadingState() =
        runTest {
            val old = CompletableDeferred<InboxSnapshot>()
            val next = CompletableDeferred<InboxSnapshot>()
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope): InboxSnapshot =
                        withContext(NonCancellable) {
                            if (scope == MailScope.Inbox) old.await() else next.await()
                        }
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            state.show(MailScope.Folder("ada", "r"))
            runCurrent()
            old.complete(InboxSnapshot(listOf(message("old"))))
            runCurrent()
            assertTrue(state.loading)
            assertTrue(state.messages.isEmpty())
            next.complete(InboxSnapshot(listOf(message("new"))))
            runCurrent()
            assertEquals(listOf("new"), state.messages.map { it.id })
            assertFalse(state.loading)
        }

    @Test
    fun aFailedRemovalCannotRestoreMailInsideAnotherFolder() =
        runTest {
            val source =
                object : MailSource {
                    override suspend fun messages(scope: MailScope) =
                        InboxSnapshot(listOf(message(if (scope == MailScope.Inbox) "inbox" else "folder")))
                }
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            val undo = state.remove("inbox")
            assertTrue(state.messages.isEmpty())
            undo.rollBack()
            assertEquals(listOf("inbox"), state.messages.map { it.id })
            val oldUndo = state.remove("inbox")
            state.show(MailScope.Folder("ada", "r"))
            runCurrent()
            oldUndo.rollBack()
            assertEquals(listOf("folder"), state.messages.map { it.id })
        }
}
