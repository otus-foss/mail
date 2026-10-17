package com.otus.mail.app

import com.otus.designsystem.components.ToastState
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutgoingMailTest {
    private val toasts = ToastState()
    private val runs = mutableListOf<Instant?>()
    private val opened = mutableListOf<ComposeRequest>()
    private val cancelled = mutableListOf<SentMessage>()
    private var failing = false
    private var hold = HOLD
    private val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    private val scheduled =
        object : ScheduledSends {
            override suspend fun maxDelaySeconds(accountId: String) = 0L

            override suspend fun cancel(message: SentMessage) = Result.success(Unit).also { cancelled += message }
        }

    private fun TestScope.outgoing() = OutgoingMail(backgroundScope, toasts, scheduled) { hold }

    private val message =
        Outgoing(
            run = { at ->
                runs += at
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
            assertEquals(listOf<Instant?>(null), runs)
            assertEquals(listOf("Sent"), toasts.messages.map { it.text })
        }

    @Test
    fun withUndoSendOffAMessageGoesAtOnce() =
        runTest {
            hold = 0L
            outgoing().send(message)
            runCurrent()
            assertEquals(listOf<Instant?>(null), runs)
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
            assertEquals("Scheduled for Mon, 8:00", toast.text)
            assertNotNull(runs.single())
            assertNotNull(toast.action).onAction()
            runCurrent()
            assertEquals(listOf(SentMessage("a1", "e9", "s1")), cancelled)
            assertEquals("e9", (opened.single() as ComposeRequest.Resume).draft.emailId)
        }

    private companion object {
        const val HOLD = 5_000L
    }
}
