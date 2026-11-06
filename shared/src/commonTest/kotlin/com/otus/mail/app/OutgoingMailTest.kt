package com.otus.mail.app

import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailOutbox
import com.otus.mail.feature.inbox.domain.OutboxStore
import com.otus.mail.feature.inbox.domain.SendCheck
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SendTime
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class OutgoingMailTest : OutgoingMailFixture() {
    @Test
    fun aMessageWaitsWithUndoThenSaysSent() =
        runTest {
            outgoing().send(message)
            assertEquals(listOf("Sending…"), toasts.messages.map { it.text })
            assertEquals(HOLD, toasts.messages.single().durationMillis)
            advanceTimeBy(HOLD - 1)
            assertTrue(runs.isEmpty(), "Nothing goes before the Undo window ends")
            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Now), runs)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun withUndoSendOffAMessageGoesAtOnce() =
        runTest {
            hold = 0L
            outgoing().send(message)
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Now), runs)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun undoKeepsTheMessageAndOpensTheDraftAgain() =
        runTest {
            outgoing().send(message)
            assertNotNull(toasts.messages.single().action).onClick()
            advanceTimeBy(HOLD * 2)
            assertTrue(runs.isEmpty())
            assertEquals(listOf<ComposeRequest>(message.reopen), opened)
        }

    @Test
    fun aFailureStaysWithRetryUntilItGoes() =
        runTest {
            failure = SendFailure.Unreachable
            outgoing().send(message)
            advanceTimeBy(HOLD + 1)
            runCurrent()
            val failed = toasts.messages.single()
            assertEquals("Will send when online", failed.text)
            assertTrue(failed.sticky)
            failure = null
            assertNotNull(failed.action).onClick()
            runCurrent()
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun undoingAScheduledMessageCancelsItAndOpensItsCopy() =
        runTest {
            outgoing().schedule(message, LocalDateTime(2026, 10, 19, 8, 0))
            runCurrent()
            val toast = toasts.messages.single()
            assertEquals("Scheduled for Mon 19 Oct, 8:00", toast.text)
            assertTrue(runs.single() is SendTime.Scheduled)
            assertNotNull(toast.action).onClick()
            runCurrent()
            assertEquals(listOf(SentMessage("a1", "e9", "s1")), cancelled)
            assertEquals("e9", (opened.single() as ComposeRequest.Resume).draft.emailId)
        }

    @Test
    fun whereTheServerCanHoldMailTheMessageGoesAtOnceAndTheServerWaits() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Held(NOW + HOLD.milliseconds + 2.seconds)), runs)
            val toast = toasts.messages.single()
            assertEquals("Sending…", toast.text)
            assertEquals(HOLD, toast.durationMillis)
            advanceTimeBy(HOLD * 2)
            assertEquals(1, runs.size, "The device sends nothing more after the window")
            assertNotNull(toast.onClose).invoke()
            runCurrent()
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun undoCancelsAServerHeldMessageAndOpensItsCopy() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()
            assertEquals(listOf(SentMessage("a1", "e9", "s1")), cancelled)
            assertEquals("e9", (opened.single() as ComposeRequest.Resume).draft.emailId)
        }

    @Test
    fun aServerThatCannotHoldLongEnoughLeavesTheHoldToTheDevice() =
        runTest {
            serverDelaySeconds = HOLD / 1_000
            outgoing().send(message)
            runCurrent()
            assertTrue(runs.isEmpty(), "The window and its spare time must fit the server's limit")
            advanceTimeBy(HOLD + 1)
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Now), runs)
        }

    @Test
    fun aRefusedServerHoldStaysWithRetry() =
        runTest {
            serverDelaySeconds = 60
            failure = SendFailure.Recipients
            outgoing().send(message)
            runCurrent()
            val failed = toasts.messages.single()
            assertEquals("Couldn't send", failed.text)
            assertTrue(failed.sticky)
        }

    @Test
    fun swipingTheToastAwaySendsAServerHeldMessageAtOnce() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            runCurrent()
            assertEquals("Send", assertNotNull(toasts.messages.single().swipeAction).label)
            swipeAway()
            runCurrent()
            assertEquals(listOf(SentMessage("a1", "e9", "s1")), sentNow)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun aSwipeNearTheEndOfTheWindowLetsTheServerSendOnTime() =
        runTest {
            serverDelaySeconds = 60
            outgoing().send(message)
            advanceTimeBy(HOLD - 2_000)
            swipeAway()
            runCurrent()
            assertTrue(sentNow.isEmpty(), "Canceling and sending again would gain under 3 seconds")
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun aRefusedEarlySendOffersTheDraftToOpen() =
        runTest {
            serverDelaySeconds = 60
            sendNowFailure = SendFailure.Recipients
            outgoing().send(message)
            runCurrent()
            swipeAway()
            runCurrent()
            val failed = toasts.messages.single()
            assertEquals("Couldn't send", failed.text)
            assertNotNull(failed.action).onClick()
            // Open runs in the process scope, so it outlives the page that showed the toast.
            runCurrent()
            assertEquals("e9", (opened.single() as ComposeRequest.Resume).draft.emailId)
        }

    @Test
    fun swipingAwayAMessageTheDeviceHoldsSendsItAtOnce() =
        runTest {
            outgoing().send(message)
            runCurrent()
            swipeAway()
            runCurrent()
            assertEquals(listOf<SendTime>(SendTime.Now), runs)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun aServerHoldWithoutAnAnswerIsLookedUpBeforeTheDeviceSendsIt() =
        runTest {
            serverDelaySeconds = 60
            failure = SendFailure.Unreachable
            outgoing().send(message)
            runCurrent()
            assertEquals("Sending…", toasts.messages.single().text, "The device takes over the hold")
            failure = null
            check = SendCheck.Sent
            advanceTimeBy(HOLD + 1)
            runCurrent()
            assertEquals(1, runs.size, "The server had the hold, so nothing goes twice")
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun aMessageAnEarlierRunCouldNotSendIsOfferedToOpen() =
        runTest {
            val store = object : MemoryMapStore(), OutboxStore {}
            val earlier = MailOutbox(store, sender, checker, io = EmptyCoroutineContext)
            failure = SendFailure.Recipients
            earlier.send(earlier.add(draft, null, NOW))

            val restarted = MailOutbox(store, sender, checker, io = EmptyCoroutineContext)
            OutgoingMail(backgroundScope, toasts, scheduled, restarted, { DAY_FIRST }) { hold }.resume { opened += it }
            runCurrent()
            val offer = toasts.messages.single()
            assertEquals("Couldn't send", offer.text)
            assertNotNull(offer.action).onClick()
            runCurrent()
            val reopened = (opened.single() as ComposeRequest.Resume).draft
            assertEquals("Lunch", reopened.subject)
            assertNull(reopened.messageId, "An edited message is a new one")
            assertTrue(restarted.offers().isEmpty())
            assertEquals(1, runs.size, "A refused message never goes again by itself")
        }

    @Test
    fun anOfferedMessageLeavesTheOutboxOnlyOnceAPageShowsIt() =
        runTest {
            val store = object : MemoryMapStore(), OutboxStore {}
            val earlier = MailOutbox(store, sender, checker, io = EmptyCoroutineContext)
            failure = SendFailure.Recipients
            earlier.send(earlier.add(draft, null, NOW))
            val pages = PageRequests(backgroundScope)

            val restarted = MailOutbox(store, sender, checker, io = EmptyCoroutineContext)
            OutgoingMail(backgroundScope, toasts, scheduled, restarted, { DAY_FIRST }) { hold }
                .resume { pages.show(MailPage.Compose(it)) }
            runCurrent()
            // The user taps Open while no activity shows pages, as during a rotation.
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()

            suspend fun kept() = MailOutbox(store, sender, checker, io = EmptyCoroutineContext).offers()
            assertEquals(1, kept().size, "The message stays until a page has it")
            val shown = mutableListOf<MailPage>()
            backgroundScope.launch { pages.collect { shown += it } }
            runCurrent()
            val request = (shown.single() as MailPage.Compose).request as ComposeRequest.Resume
            assertEquals("Lunch", request.draft.subject)
            assertTrue(kept().isEmpty())
        }
}
