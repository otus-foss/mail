package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SendTime
import com.otus.mail.feature.inbox.domain.SentMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutgoingMailTest {
    private val toasts = ToastState()
    private val runs = mutableListOf<SendTime>()
    private val opened = mutableListOf<ComposeRequest>()
    private val cancelled = mutableListOf<SentMessage>()
    private var failing = false
    private var hold = HOLD
    private var serverDelaySeconds = 0L
    private val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    private val scheduled =
        object : ScheduledSends {
            override suspend fun maxDelaySeconds(accountId: String) = serverDelaySeconds

            override suspend fun cancel(message: SentMessage) = Result.success(Unit).also { cancelled += message }
        }

    private fun TestScope.outgoing() = OutgoingMail(backgroundScope, toasts, scheduled, { NOW }) { hold }

    private val message =
        Outgoing(
            run = { time ->
                runs += time
                if (failing) {
                    Result.failure(
                        SendException(SendFailure.Unreachable),
                    )
                } else {
                    Result.success(SentMessage("a1", "e9", "s1"))
                }
            },
            reopen = ComposeRequest.Resume(draft, ComposeRequest.New()),
            open = { opened += it },
        )

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
            assertNotNull(toasts.messages.single().action).onAction()
            advanceTimeBy(HOLD * 2)
            assertTrue(runs.isEmpty())
            assertEquals(listOf<ComposeRequest>(message.reopen), opened)
        }

    @Test
    fun aFailureStaysWithRetryUntilItGoes() =
        runTest {
            failing = true
            outgoing().send(message)
            advanceTimeBy(HOLD + 1)
            runCurrent()
            val failed = toasts.messages.single()
            assertEquals("Couldn't send", failed.text)
            assertTrue(failed.sticky)
            failing = false
            assertNotNull(failed.action).onAction()
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
            assertNotNull(toast.action).onAction()
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
            assertNotNull(toasts.messages.single().action).onAction()
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
            failing = true
            outgoing().send(message)
            runCurrent()
            val failed = toasts.messages.single()
            assertEquals("Couldn't send", failed.text)
            assertTrue(failed.sticky)
        }

    private companion object {
        const val HOLD = 5_000L
        val NOW = Instant.parse("2026-10-30T12:00:00Z")
    }
}
