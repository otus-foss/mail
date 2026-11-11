package com.otusfoss.mail.app

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.feature.compose.domain.SendCheck
import com.otusfoss.mail.testing.OutgoingMailFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Undo and swipe-to-send while the server cannot be reached. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineUndoTest : OutgoingMailFixture() {
    @Test
    fun undoOfAHoldWithoutAnAnswerOpensTheDraftAndCancelsTheHoldLater() =
        runTest {
            serverDelaySeconds = 60
            failure = SendFailure.Mail(MailFailure.Unreachable)
            val outgoing = outgoing()
            outgoing.send(message)
            runCurrent()
            assertNotNull(toasts.messages.single().action).onClick()
            runCurrent()
            assertEquals(listOf<ComposeRequest>(message.reopen), opened, "The draft opens at once")
            advanceTimeBy(HOLD + 1)
            runCurrent()
            assertEquals(1, runs.size, "Only the hold was tried")

            failure = null
            check = SendCheck.NotSent
            outgoing.resume { }
            runCurrent()
            assertEquals(1, runs.size, "Online, it still never goes")
            assertEquals(listOf(true), checks, "The server is asked to cancel a hold it has")
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
