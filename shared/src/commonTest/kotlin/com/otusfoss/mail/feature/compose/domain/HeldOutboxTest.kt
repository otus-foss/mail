package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.FakeScheduledSends
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A message the server holds for Undo stays in the outbox, so Undo and sending it early outlive the app. */
class HeldOutboxTest {
    private val store = MemoryMapStore()
    private val server = FakeScheduledSends()
    private val heldCancels = mutableListOf<Boolean>()
    private var sends = 0
    private var failure: SendFailure? = null
    private var check = SendCheck.NotSent
    private var now = NOW
    private val draft = Draft("a1", subject = "Lunch", emailId = "d1")
    private val held = SentMessage("a1", "e9", "s1")

    private val sender =
        DraftSender { _, _, _ ->
            sends++
            failure?.let { Result.failure(SendException(it)) } ?: Result.success(held)
        }

    private val checker =
        SendChecker { _, _, cancelHeld ->
            heldCancels += cancelHeld
            check
        }

    /** An outbox as a new process builds it; the sender stores the copy `e9` before it submits it. */
    private fun outbox() =
        MailOutbox(
            DeviceStore(store, OutboxStore),
            OutboxServer(sender.storingCopy("e9"), checker, server),
            now = { now },
        )

    private fun saved() = OutboxStore.ENTRIES.of(store.saved).orEmpty()

    /** A message the server holds until the Undo window ends, plus its spare time. */
    private suspend fun MailOutbox.hold(): String {
        val id = add(draft, null, due = NOW + UNDO_WINDOW)
        assertTrue(send(id, holdUntil = NOW + UNDO_WINDOW + 2.seconds).isSuccess)
        return id
    }

    @Test
    fun aHeldMessageStaysUntilItsHoldIsOver() =
        runTest {
            val outbox = outbox()
            outbox.hold()
            assertEquals(held, saved().single().held)
            assertFalse(outbox.waiting(), "The server sends it by itself, so no background run is needed")

            now = NOW + UNDO_WINDOW
            outbox.resume()
            assertEquals(1, saved().size, "The server may still hold it for its spare time")
            now = NOW + UNDO_WINDOW + 2.seconds
            outbox.resume()
            assertTrue(saved().isEmpty())
            assertEquals(1, sends, "Nothing goes twice")
        }

    @Test
    fun undoOfAHoldWhoseAnswerWasLostAsksTheServerAtOnce() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, due = NOW + UNDO_WINDOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            assertTrue(outbox.send(id, holdUntil = NOW + UNDO_WINDOW + 2.seconds).isFailure, "The answer was lost")

            now = NOW + 5.seconds
            assertEquals(SendCancel.Stopped, outbox.cancel(id))
            assertEquals(listOf(true), heldCancels, "The hold is canceled 12 seconds before the server would send")
            assertTrue(saved().isEmpty())
        }

    @Test
    fun undoThatCannotReachTheServerAsksAgainBeforeTheUndoWindowEnds() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, due = NOW + UNDO_WINDOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outbox.send(id, holdUntil = NOW + UNDO_WINDOW + 2.seconds)
            check = SendCheck.Unreachable
            now = NOW + 5.seconds
            assertEquals(SendCancel.Waiting, outbox.cancel(id))
            assertTrue(outbox.waiting(), "A background run is due")

            check = SendCheck.Sent
            now = NOW + 6.seconds
            outbox().resume()
            assertEquals(listOf(true, true), heldCancels, "A new process asks again at once, not at the window's end")
            assertTrue(saved().isEmpty())
            assertEquals(1, sends, "A withdrawn message never goes again")
        }

    @Test
    fun undoOfAHeldMessageThatCannotReachTheServerIsFinishedAfterARestart() =
        runTest {
            val outbox = outbox()
            val id = outbox.hold()
            server.cancelOutcome = SendCancel.Waiting
            assertEquals(SendCancel.Waiting, outbox.cancel(id))
            assertTrue(saved().single().withdrawn, "Undo is kept before the server answers")

            server.cancelOutcome = SendCancel.Stopped
            val restarted = outbox()
            restarted.resume()
            assertEquals(listOf(held), server.cancelled, "The hold is canceled and its copy a draft again")
            assertTrue(saved().isEmpty())
            assertEquals(SendCancel.Stopped, restarted.cancel(id), "An Undo that asks again hears how it went")
        }

    @Test
    fun aMessageSentEarlyWhileOfflineGoesAfterARestart() =
        runTest {
            val outbox = outbox()
            val id = outbox.hold()
            server.sendNowFailure = SendFailure.Mail(MailFailure.Unreachable)
            assertTrue(outbox.sendNow(id).isFailure)
            assertTrue(saved().single().sendNow, "The hold may be canceled with nothing in its place, so it is kept")
            assertTrue(outbox.waiting())

            server.sendNowFailure = null
            // Past the hold's time: the cancel may have arrived, so the server is asked all the same.
            now = NOW + UNDO_WINDOW + 30.seconds
            outbox().resume()
            assertEquals(listOf(held, held), server.sentNow)
            assertTrue(saved().isEmpty())
        }

    @Test
    fun sendingEarlyWithLittleOfTheHoldLeftLetsTheServerSendOnTime() =
        runTest {
            val outbox = outbox()
            val id = outbox.hold()
            now = NOW + UNDO_WINDOW - 3.seconds
            assertTrue(outbox.sendNow(id).isSuccess)
            assertTrue(server.sentNow.isEmpty(), "Canceling and sending again would gain 3 seconds at most")
            assertTrue(saved().isEmpty())
        }

    @Test
    fun aRefusedEarlySendWaitsForTheUserWithItsCopy() =
        runTest {
            val outbox = outbox()
            val id = outbox.hold()
            server.sendNowFailure = SendFailure.Recipients
            assertTrue(outbox.sendNow(id).isFailure)
            val kept = saved().single()
            assertEquals(OutboxProblem.Refused, kept.problem)
            assertEquals("e9", kept.draft.emailId, "The copy is a draft again, so the message opens as that copy")
            assertNull(kept.held)
            assertFalse(outbox.waiting(), "It never goes by itself")
            assertTrue(outbox.sendNow(id).isFailure, "Asking again says the same")
            assertEquals(1, server.sentNow.size)
            assertEquals(listOf(id), outbox().offers().map { it.id }, "After a restart it is offered to open")
        }

    @Test
    fun anEarlySendNobodyCanConfirmWaitsForTheUserApartFromItsCopy() =
        runTest {
            val outbox = outbox()
            val id = outbox.hold()
            server.sendNowFailure = SendFailure.Unconfirmed
            assertEquals(SendFailure.Unconfirmed, (outbox.sendNow(id).exceptionOrNull() as SendException).failure)
            val kept = saved().single()
            assertEquals(OutboxProblem.Unconfirmed, kept.problem)
            assertNull(kept.draft.emailId, "The copy may be the sent message, so the draft does not replace it")
        }

    @Test
    fun aDiscardedEditOfAScheduledMailIsCanceledOnceItsUndoHasPassedAlsoAfterARestart() =
        runTest {
            val copies = mutableListOf<String?>()
            val first = outbox()
            first.discard(held, due = NOW + 6.seconds)
            assertEquals("a1", saved().single().draft.accountId, "Removing the account drops it")

            val restarted =
                MailOutbox(
                    DeviceStore(store, OutboxStore),
                    OutboxServer(sender, checker.recordingCopies(copies), server),
                    now = { now },
                )
            now = NOW + 5.seconds
            restarted.resume()
            assertTrue(heldCancels.isEmpty(), "Undo of the discard can still keep the send")

            now = NOW + 6.seconds
            restarted.resume()
            assertEquals(listOf(true), heldCancels, "The send is canceled and its copy destroyed")
            assertEquals(listOf<String?>("e9"), copies, "By the id of the scheduled copy")
            assertTrue(saved().isEmpty())
            assertEquals(0, sends, "A discarded message never goes")
        }

    @Test
    fun undoOfADiscardKeepsTheScheduledSend() =
        runTest {
            val outbox = outbox()
            val id = outbox.discard(held, due = NOW + 6.seconds)
            assertTrue(outbox.drop(id), "The discard was still waiting")
            now = NOW + 6.seconds
            outbox.resume()
            assertTrue(heldCancels.isEmpty())
            assertFalse(outbox.drop(id), "A second Undo finds nothing")
        }

    private companion object {
        val NOW = Instant.parse("2026-11-25T12:00:00Z")

        /** The default Undo send delay. */
        val UNDO_WINDOW = 15.seconds
    }
}
