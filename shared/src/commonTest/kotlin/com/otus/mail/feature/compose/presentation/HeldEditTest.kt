package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.DraftFailure
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.presentation.actions.PageSubject
import com.otus.mail.core.messages.presentation.actions.ScheduledActions
import com.otus.mail.core.messages.presentation.actions.offerScheduled
import com.otus.mail.feature.inbox.presentation.actions.offerMessage
import com.otus.mail.testing.FakeDrafts
import com.otus.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A draft that edits a scheduled send leaves that send alone until Send replaces it. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HeldEditTest : OutgoingMailFixture() {
    private val held = SentMessage("a1", "d1", "s1")
    private val editing = ComposeRequest.Resume(draft, ComposeRequest.New("a1"), held = held)

    private fun TestScope.state(store: FakeDrafts) =
        ComposeState(
            ComposeServices(store, { _, _ -> emptyList() }),
            backgroundScope,
            editing,
            ComposeOrigin("a1", listOf(ComposeAccount("a1", "Work")), listOf("me@example.com")),
            saveDelayMillis = DELAY,
        )

    @Test
    fun editingAScheduledSendSavesNothingSoLeavingKeepsItAsItWas() =
        runTest {
            val state = state(drafts)
            state.edit { it.copy(subject = "Dinner") }
            advanceTimeBy(DELAY + 1)
            state.close()
            runCurrent()
            assertTrue(drafts.saved.isEmpty(), "A save would replace the copy the server still sends")
            assertTrue(drafts.discarded.isEmpty())
        }

    @Test
    fun undoAfterDiscardStillEditsTheScheduledSend() =
        runTest {
            val state = state(FakeDrafts())
            state.edit { it.copy(subject = "Dinner") }
            state.discard()
            assertEquals(held, state.reopening().held)
            assertEquals("Dinner", state.reopening().draft.subject)
        }

    @Test
    fun aReleasedEditNoLongerEditsTheScheduledSend() {
        val free = mail(editing).released()
        assertNull(free.reopen.held, "Undo opens an ordinary draft: the send is gone")
        assertEquals("d1", free.reopen.draft.emailId)
    }

    @Test
    fun sendReplacesTheScheduledSendAtItsNewTime() =
        runTest {
            edits().send(mail(editing), LocalDateTime(2026, 11, 2, 9, 0))
            runCurrent()
            assertEquals(listOf(held), cancelled)
            assertTrue(runs.single() is SendTime.Scheduled)
        }

    @Test
    fun aSendThatStillWaitsKeepsTheEditOnOffer() =
        runTest {
            cancelOutcome = SendCancel.Waiting
            edits().send(mail(editing), at = null)
            runCurrent()
            assertTrue(runs.isEmpty(), "Sending as well would send the mail twice")
            val toast = toasts.messages.single()
            assertEquals(COULD_NOT_CHANGE, toast.text)
            assertTrue(toast.sticky, "The edit is saved nowhere else, so the offer waits for the user")
            assertNotNull(toast.action).onClick()
            runCurrent()
            assertEquals(listOf<ComposeRequest>(editing), opened)
        }

    @Test
    fun anEditOfAMailThatWentIsSavedAsANewDraft() =
        runTest {
            drafts.save(draft)
            cancelOutcome = SendCancel.Final
            edits().send(mail(editing), at = null)
            runCurrent()
            assertTrue(runs.isEmpty(), "Only the user can tell whether the edit should go as well")
            assertNull(drafts.saved.last().emailId, "A save over the sent copy would replace it")
            assertEquals(listOf("a1"), filedSent, "The sent copy moves to Sent")
            val toast = toasts.messages.single()
            assertEquals("$ALREADY_SENT. $SAVED_EDIT", toast.text)
            assertNotNull(toast.action).onClick()
            runCurrent()
            val reopened = opened.single() as ComposeRequest.Resume
            assertNull(reopened.held)
            assertEquals("d2", reopened.draft.emailId, "Open shows the saved draft")
        }

    @Test
    fun anEditTheServerCannotPlaceStaysOnOfferWhenItCannotBeSaved() =
        runTest {
            drafts.failure = DraftFailure.Mail(MailFailure.Unreachable)
            cancelOutcome = SendCancel.Unknown
            edits().send(mail(editing), at = null)
            runCurrent()
            val toast = toasts.messages.single()
            assertEquals(NOT_CONFIRMED, toast.text)
            assertTrue(toast.sticky)
            assertNotNull(toast.action).onClick()
            runCurrent()
            val reopened = opened.single() as ComposeRequest.Resume
            assertNull(reopened.draft.emailId)
            assertNull(reopened.held)
        }

    @Test
    fun discardCancelsTheScheduledSendBeforeItDeletesTheCopy() =
        runTest {
            var deleted = 0
            edits().discard(held) { deleted++ }
            runCurrent()
            assertEquals(listOf(held), cancelled)
            assertEquals(1, deleted)

            edits().discard(held = null) { deleted++ }
            assertEquals(2, deleted)
        }

    @Test
    fun aDiscardWhoseSendStillWaitsSaysSoAndRetries() =
        runTest {
            cancelOutcome = SendCancel.Waiting
            var deleted = 0
            edits().discard(held) { deleted++ }
            runCurrent()
            assertEquals(0, deleted, "A copy whose send still waits stays")
            val toast = toasts.messages.single()
            assertEquals(MAY_STILL_SEND, toast.text)
            assertTrue(toast.sticky)

            cancelOutcome = SendCancel.Stopped
            assertNotNull(toast.action).onClick()
            runCurrent()
            assertEquals(1, deleted)
        }

    @Test
    fun discardKeepsTheCopyOfAMailThatWentOrMayHave() =
        runTest {
            var deleted = 0
            cancelOutcome = SendCancel.Final
            edits().discard(held) { deleted++ }
            runCurrent()
            cancelOutcome = SendCancel.Unknown
            edits().discard(held) { deleted++ }
            runCurrent()
            assertEquals(0, deleted, "The copy is the sent message, or may be")
            assertEquals(listOf(ALREADY_SENT, NOT_CONFIRMED), toasts.messages.map { it.text })
            assertEquals(listOf("a1"), filedSent)
        }

    @Test
    fun editKeepsTheMessageOpenAndSendNowTakesItAway() {
        val ran = mutableListOf<String>()
        val actions = ScheduledActions(edit = { ran += "edit" }, sendNow = { ran += "now" })
        val page = PageSubject(offerMessage("1"), MailboxRole.Scheduled, onCompose = {}, scheduled = actions)
        val edit = assertNotNull(page.offerScheduled(MailAction.EditScheduled))
        val now = assertNotNull(page.offerScheduled(MailAction.SendNow))
        assertFalse(edit.leaves, "Write opens over the message, in one slide")
        assertTrue(now.leaves)
        edit.run()
        now.run()
        assertEquals(listOf("edit", "now"), ran)
        assertNull(PageSubject(offerMessage("1"), MailboxRole.Inbox, {}, actions).offerScheduled(MailAction.SendNow))
    }

    @Test
    fun anOrdinaryDraftGoesAsItIs() =
        runTest {
            edits().send(message, LocalDateTime(2026, 11, 2, 9, 0))
            runCurrent()
            assertTrue(cancelled.isEmpty())
            assertTrue(runs.single() is SendTime.Scheduled)
        }

    private fun TestScope.edits() = HeldEdits(backgroundScope, toasts, scheduled, drafts, outgoing())

    private fun mail(reopen: ComposeRequest.Resume) =
        Outgoing(OutgoingDraft(ready = { draft }, answered = null, sent = {}), reopen, open = { opened += it })

    private companion object {
        const val DELAY = 1_000L
    }
}
