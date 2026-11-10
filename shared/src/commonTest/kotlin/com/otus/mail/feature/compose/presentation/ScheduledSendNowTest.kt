package com.otus.mail.feature.compose.presentation

import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Send now on a scheduled mail sends its copy only once the scheduled send is stopped. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ScheduledSendNowTest : OutgoingMailFixture() {
    private val held = SentMessage("a1", "d1", "s1")
    private var filings = 0

    @Test
    fun aStoppedSendGoesAtOnce() =
        runTest {
            sendNow().send(held, copy())
            advanceTimeBy(HOLD + 1)
            assertEquals(listOf(held), cancelled)
            assertEquals(listOf<SendTime>(SendTime.Now), runs)
        }

    @Test
    fun aSendThatStillWaitsOffersRetryUntilTheUserActs() =
        runTest {
            cancelOutcome = SendCancel.Waiting
            sendNow().send(held, copy())
            runCurrent()
            assertTrue(runs.isEmpty(), "The scheduled send still goes, so the copy must not go too")
            val toast = toasts.messages.single()
            assertEquals(COULD_NOT_CHANGE, toast.text)
            assertTrue(toast.sticky)

            cancelOutcome = SendCancel.Stopped
            assertNotNull(toast.action).onClick()
            advanceTimeBy(HOLD + 1)
            assertEquals(listOf(held), cancelled)
            assertEquals(1, runs.size)
        }

    @Test
    fun aMailThatWentIsFiledInSentAndNotSentAgain() =
        runTest {
            cancelOutcome = SendCancel.Final
            sendNow().send(held, copy())
            runCurrent()
            assertTrue(runs.isEmpty())
            assertEquals(ALREADY_SENT, toasts.messages.single().text)
            assertEquals(listOf("a1"), filedSent)
            assertEquals(1, filings, "Open lists show it in Sent")
        }

    @Test
    fun aSendTheServerCannotPlaceIsNotSentAgain() =
        runTest {
            cancelOutcome = SendCancel.Unknown
            sendNow().send(held, copy())
            runCurrent()
            assertTrue(runs.isEmpty())
            assertEquals(NOT_CONFIRMED, toasts.messages.single().text)
        }

    private fun TestScope.sendNow() = ScheduledSendNow(backgroundScope, toasts, scheduled, outgoing())

    private fun copy() =
        Outgoing(
            OutgoingDraft(ready = { draft }, answered = null, sent = {}),
            ComposeRequest.Resume(draft, ComposeRequest.New("a1")),
            filed = { filings++ },
            open = { opened += it },
        )
}
