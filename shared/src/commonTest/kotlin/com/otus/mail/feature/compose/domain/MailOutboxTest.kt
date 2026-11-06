package com.otus.mail.feature.compose.domain

import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.DraftSender
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.messages.domain.accountId
import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MailOutboxTest {
    private class Store :
        MemoryMapStore(),
        OutboxStore

    private val store = Store()
    private val sent = mutableListOf<Pair<Draft, SendTime>>()
    private val checks = mutableListOf<Draft>()
    private val heldCancels = mutableListOf<Boolean>()
    private var failure: SendFailure? = null
    private var check = SendCheck.NotSent
    private var now = NOW
    private val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    private val sender =
        DraftSender { draft, _, time ->
            sent += draft to time
            failure
                ?.let { Result.failure<SentMessage>(SendException(it)) }
                ?: Result.success(SentMessage("a1", "e9", "s1"))
        }

    private val checker =
        SendChecker { draft, _, cancelHeld ->
            checks += draft
            heldCancels += cancelHeld
            check
        }

    private fun outbox() = MailOutbox(store, sender, checker, now = { now })

    @Test
    fun aMessageOutlivesTheAppUntilItIsDue() =
        runTest {
            outbox().add(draft, null, due = NOW + 10.seconds)
            val restarted = outbox()
            restarted.resume()
            assertTrue(sent.isEmpty(), "It waits for its time")

            now = NOW + 10.seconds
            restarted.resume()
            val (went, time) = sent.single()
            assertEquals(SendTime.Now, time)
            assertEquals("Lunch", went.subject)
            assertNotNull(went.messageId, "Each message gets a Message-ID to find it by")
            assertTrue(OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).isEmpty())
        }

    @Test
    fun aSendWhoseAnswerWasLostIsLookedUpBeforeItGoesAgain() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            assertTrue(outbox.send(id).isFailure)
            assertTrue(outbox.waiting(), "It waits for the network")

            failure = null
            check = SendCheck.Sent
            assertNull(outbox.send(id).getOrThrow(), "The server already had it")
            assertEquals(1, sent.size, "Nothing goes twice")
            assertEquals(sent.single().first.messageId, checks.single().messageId)
            assertFalse(outbox.waiting())
        }

    @Test
    fun aMessageTheServerNeverGotGoesAfterTheLookup() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outbox.send(id)
            failure = null
            assertTrue(outbox.send(id).isSuccess)
            assertEquals(2, sent.size)
            assertEquals(1, checks.size)
        }

    @Test
    fun aSendNobodyCanConfirmWaitsForTheUserAndIsOfferedOnce() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outbox.send(id)
            check = SendCheck.Unconfirmable
            assertTrue(outbox.send(id).isFailure)

            val restarted = outbox()
            restarted.resume()
            assertEquals(1, sent.size, "It never goes by itself")
            assertEquals(OutboxProblem.Unconfirmed, restarted.offers().single().problem)
            assertTrue(restarted.offers().isEmpty(), "A recreated screen does not offer it again")
            restarted.drop(id)
            assertTrue(OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).isEmpty())
        }

    @Test
    fun aRefusedMessageWaitsForARetryThatNeedsNoLookup() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Recipients
            assertTrue(outbox.send(id).isFailure)
            outbox.resume()
            assertEquals(1, sent.size, "A refused message does not go again by itself")
            assertTrue(outbox.offers().isEmpty(), "The failed send's own toast already told the user")

            failure = null
            assertTrue(outbox.send(id).isSuccess)
            assertTrue(checks.isEmpty())
        }

    @Test
    fun aServerProblemThatMayHideALostAnswerIsLookedUpBeforeARetry() =
        runTest {
            val outbox = outbox()
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.ServerProblem)
            assertTrue(outbox.send(id).isFailure)
            failure = null
            check = SendCheck.Sent
            assertNull(outbox.send(id).getOrThrow())
            assertEquals(1, sent.size, "The gateway error hid a send that went")
        }

    @Test
    fun aLostSubmissionIsLookedUpByItsStoredCopyOnceTheServerHadTimeToFinishIt() =
        runTest {
            val copies = mutableListOf<String?>()
            val woken = mutableListOf<Instant>()
            val outbox =
                MailOutbox(store, storing, checker.recordingCopies(copies), wake = { woken += it }, now = { now })
            val id = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outbox.send(id)
            assertEquals(
                "e9",
                OutboxEntry
                    .decode(store.saved[OutboxStore.ENTRIES])
                    .single()
                    .storedCopy
                    ?.emailId,
            )

            failure = null
            check = SendCheck.Sent
            now = NOW + 30.seconds
            assertEquals(
                SendFailure.Mail(MailFailure.Unreachable),
                (outbox.send(id).exceptionOrNull() as SendException).failure,
            )
            assertTrue(copies.isEmpty(), "The server may still be working on the submission")
            assertEquals(NOW + 2.minutes, woken.last())

            now = NOW + 2.minutes
            assertNull(outbox.send(id).getOrThrow(), "The first submission went")
            assertEquals(listOf<String?>("e9"), copies)
            assertEquals(1, sent.size, "Nothing goes twice")
        }

    private val storing = sender.storingCopy("e9")

    @Test
    fun undoDropsAWaitingMessageButNotOneThatWent() =
        runTest {
            val outbox = outbox()
            assertTrue(outbox.cancel(outbox.add(draft, null, NOW + 5.seconds)))
            assertTrue(OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).isEmpty())
            val went = outbox.add(draft, null, NOW)
            outbox.send(went)
            assertFalse(outbox.cancel(went))
        }

    @Test
    fun undoOfAMessageTheServerMayHoldNeverSendsItAndCancelsTheHold() =
        runTest {
            val outbox = outbox()
            val sending = outbox.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.Unreachable)
            outbox.send(sending)
            assertTrue(outbox.cancel(sending), "It is withdrawn")

            check = SendCheck.Unreachable
            outbox.resume()
            assertTrue(outbox.waiting(), "It waits until the server can be asked")
            check = SendCheck.NotSent
            outbox.resume()
            assertEquals(1, sent.size, "A withdrawn message never goes again")
            assertEquals(listOf(true, true), heldCancels, "A hold found there is canceled")
            assertTrue(OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).isEmpty())
        }

    @Test
    fun aScheduledMessageKeepsItsTimeAndAHeldOneGetsItsHold() =
        runTest {
            val outbox = outbox()
            val at = NOW + 600.seconds
            outbox.send(outbox.add(draft, null, NOW, sendAt = at))
            outbox.send(outbox.add(draft, null, NOW), holdUntil = NOW + 7.seconds)
            assertEquals(listOf(SendTime.Scheduled(at), SendTime.Held(NOW + 7.seconds)), sent.map { it.second })
        }

    @Test
    fun aSendThatThrowsWaitsForTheUserInsteadOfFailingEveryStart() =
        runTest {
            var tries = 0
            val throwing =
                DraftSender { _, _, _ ->
                    tries++
                    throw IllegalArgumentException("Not base64")
                }
            val outbox = MailOutbox(store, throwing, checker, now = { now })
            val id = outbox.add(draft, null, NOW)
            outbox.resume()
            val restarted = MailOutbox(store, throwing, checker, now = { now })
            restarted.resume()
            assertEquals(OutboxProblem.Refused, restarted.offers().single().problem)
            assertEquals(1, tries, "It does not go again by itself")

            check = SendCheck.Sent
            val unsure = MailOutbox(store, sender, { _, _, _ -> error("Unreadable answer") }, now = { now })
            assertEquals(SendFailure.Unconfirmed, (unsure.send(id).exceptionOrNull() as SendException).failure)
            assertTrue(sent.isEmpty(), "A lookup that throws never lets the message go again")
        }

    @Test
    fun messagesThatNoLongerReadAreSetAsideInsteadOfOverwritten() =
        runTest {
            store.saved = mapOf(OutboxStore.ENTRIES to "[{\"from an older\": \"format\"}]")
            outbox().add(draft, null, NOW)
            assertEquals("[{\"from an older\": \"format\"}]", store.saved[OutboxStore.UNREADABLE])
            assertEquals(listOf("Lunch"), OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).map { it.draft.subject })
        }

    @Test
    fun removingAnAccountDropsItsMessages() =
        runTest {
            val outbox = outbox()
            outbox.add(draft, null, NOW + 5.seconds)
            outbox.add(draft.copy(accountId = "b2"), null, NOW + 5.seconds)
            outbox.forget("a1")
            assertEquals(listOf("b2"), OutboxEntry.decode(store.saved[OutboxStore.ENTRIES]).map { it.draft.accountId })
        }

    private companion object {
        val NOW = Instant.parse("2026-10-31T12:00:00Z")
    }
}
