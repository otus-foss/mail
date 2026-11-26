package com.otusfoss.mail.app

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.feature.compose.domain.MailOutbox
import com.otusfoss.mail.feature.compose.domain.OutboxStore
import com.otusfoss.mail.feature.compose.domain.SendCheck
import com.otusfoss.mail.feature.compose.presentation.OutgoingMail
import com.otusfoss.mail.testing.DAY_FIRST
import com.otusfoss.mail.testing.MemoryMapStore
import com.otusfoss.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Undo and swipe-to-send while the server cannot be reached. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineUndoTest : OutgoingMailFixture() {
    @Test
    fun undoOfAHoldWithoutAnAnswerOpensTheDraftAndCancelsTheHoldAtOnce() =
        runTest {
            serverDelaySeconds = 60
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outgoing().send(message)
            runCurrent()
            assertEquals(1, runs.size, "The hold was tried and got no answer")

            failure = null
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()
            assertEquals(listOf<ComposeRequest>(message.reopen), opened, "The draft opens at once")
            assertEquals(listOf(true), checks, "The server is asked to cancel a hold it has while that still waits")
            advanceTimeBy(HOLD + 1)
            runCurrent()
            assertEquals(1, runs.size, "It never goes")
        }

    @Test
    fun undoThatCameTooLateForAHoldWithoutAnAnswerSaysSo() =
        runTest {
            serverDelaySeconds = 60
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outgoing().send(message)
            runCurrent()
            check = SendCheck.Unreachable
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()
            assertEquals(
                listOf<ComposeRequest>(message.reopen),
                opened,
                "The draft opens while the server is out of reach",
            )

            check = SendCheck.Sent
            advanceTimeBy(RETRY_WAIT)
            runCurrent()
            assertEquals(listOf(true, true), checks, "The server is asked again while the app runs")
            assertEquals(listOf("Already sent"), toasts.messages.map { it.text })
        }

    @Test
    fun undoAfterTheMessageWentSaysSo() =
        runTest {
            outgoing().send(message)
            val undo = assertNotNull(toasts.messages.single().action)
            swipeAway()
            runCurrent()
            undo.onClick()
            runCurrent()
            assertEquals(listOf("Already sent"), toasts.messages.map { it.text })
            assertEquals(emptyList(), opened)
        }

    @Test
    fun swipingOfflineTriesAgainUntilTheServerAnswers() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            runCurrent()
            sendNowFailure = SendFailure.Mail(MailFailure.Unreachable)
            swipeAway()
            runCurrent()
            assertEquals(listOf("Will send when online"), toasts.messages.map { it.text })

            sendNowFailure = null
            advanceTimeBy(RETRY_WAIT)
            runCurrent()
            assertEquals(2, sentNow.size)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun aMessageSwipedToSendNowWhileOfflineStaysInTheOutbox() =
        runTest {
            val memory = MemoryMapStore()
            val now = { NOW + testScheduler.currentTime.milliseconds }
            val store = DeviceStore(memory, OutboxStore)
            val outbox = MailOutbox(store, sender, checker, scheduled, now = now, io = EmptyCoroutineContext)
            serverDelaySeconds = 60
            OutgoingMail(backgroundScope, toasts, scheduled, outbox, { DAY_FIRST }, now) { hold }.send(message)
            runCurrent()
            sendNowFailure = SendFailure.Mail(MailFailure.Unreachable)
            swipeAway()
            runCurrent()
            assertEquals(listOf("Will send when online"), toasts.messages.map { it.text })

            val kept = OutboxStore.ENTRIES.of(memory.saved).orEmpty()
            assertEquals(listOf("Lunch"), kept.map { it.draft.subject }, "A later start finishes the send")
            assertTrue(kept.single().sendNow)
        }

    @Test
    fun anEarlySendNobodyCanConfirmOffersTheDraftApartFromItsCopy() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            runCurrent()
            sendNowFailure = SendFailure.Unconfirmed
            swipeAway()
            runCurrent()
            val offer = toasts.messages.single()
            assertEquals("Couldn't confirm sending", offer.text)
            assertNotNull(offer.action).onClick()
            runCurrent()
            assertNull((opened.single() as ComposeRequest.Resume).draft.emailId, "The copy may be the sent message")
        }

    @Test
    fun aMessageScheduledForATimeThatHasPassedGoesAtOnceAndSaysSent() =
        runTest {
            outgoing().schedule(message, LocalDateTime(2026, 10, 12, 8, 0))
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Now), runs, "Write stayed open past the time")
            val toast = toasts.messages.single()
            assertEquals("Sent", toast.text)
            assertNull(toast.action, "Nothing is left to undo")
        }

    @Test
    fun aSendNobodyCanConfirmOffersTheMessageToOpenAtOnce() =
        runTest {
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outgoing().send(message)
            advanceTimeBy(HOLD + 1)
            runCurrent()
            check = SendCheck.Unconfirmable
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()
            val offer = toasts.messages.single()
            assertEquals("Couldn't confirm sending", offer.text)
            assertNotNull(offer.action).onClick()
            runCurrent()
            assertEquals(listOf<ComposeRequest>(message.reopen), opened)
            assertEquals(1, runs.size, "It never goes again by itself")
        }

    private companion object {
        const val RETRY_WAIT = 5_001L
    }
}
