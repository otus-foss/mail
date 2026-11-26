package com.otusfoss.mail.feature.compose.presentation

import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import com.otusfoss.mail.feature.compose.domain.SendCheck
import com.otusfoss.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Discarding a draft that edited a scheduled send cancels that send, whatever becomes of the toast. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HeldDiscardTest : OutgoingMailFixture() {
    private val held = SentMessage("a1", "d1", "s1")
    private val editing = ComposeRequest.Resume(draft, ComposeRequest.New("a1"), held = held)

    private fun TestScope.discards(): HeldDiscards {
        val now = { NOW + testScheduler.currentTime.milliseconds }
        val outbox = MailOutbox(null, sender, checker, scheduled, now = now)
        return HeldDiscards(backgroundScope, toasts, scheduled, outbox, now)
    }

    @Test
    fun aDiscardCancelsTheScheduledSendOnceUndoHasPassedWithoutItsToastClosing() =
        runTest {
            discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS - 1)
            runCurrent()
            assertTrue(checks.isEmpty(), "Undo can still keep the send")

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf(true), checks, "The send is canceled and its copy deleted")
            assertTrue(toasts.messages.isEmpty(), "The discard went as its toast said")
            assertTrue(runs.isEmpty(), "A discarded message never goes")
        }

    @Test
    fun undoOfADiscardKeepsTheScheduledSend() =
        runTest {
            val undo = discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS / 2)
            assertEquals(editing, undo(editing), "The draft still edits the send")
            advanceTimeBy(DISCARD_UNDO_MILLIS)
            runCurrent()
            assertTrue(checks.isEmpty(), "The send waits unchanged")
        }

    @Test
    fun undoAfterTheSendWasCanceledOpensAnOrdinaryDraft() =
        runTest {
            val undo = discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS + 1)
            runCurrent()
            val reopened = undo(editing)
            assertNull(reopened.held, "No send is left to edit")
            assertNull(reopened.draft.emailId, "Its copy is gone, so the first save stores a new one")
            assertEquals("Lunch", reopened.draft.subject)
        }

    @Test
    fun aDiscardWhoseSendStillWaitsSaysSoAndRetries() =
        runTest {
            check = SendCheck.Unreachable
            discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS + 1)
            runCurrent()
            val toast = toasts.messages.single()
            assertEquals(MAY_STILL_SEND, toast.text)
            assertTrue(toast.sticky)

            check = SendCheck.NotSent
            assertNotNull(toast.action).onClick()
            runCurrent()
            assertEquals(listOf(true, true), checks)
        }

    @Test
    fun aDiscardOfAMailThatWentSaysSoAndFilesIt() =
        runTest {
            check = SendCheck.Sent
            discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(ALREADY_SENT), toasts.messages.map { it.text })
            assertEquals(listOf("a1"), filedSent, "The sent copy moves to Sent")

            check = SendCheck.Unconfirmable
            discards().discard(held)
            advanceTimeBy(DISCARD_UNDO_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(ALREADY_SENT, NOT_CONFIRMED), toasts.messages.map { it.text })
        }
}
