package com.otus.mail.feature.inbox.domain

import com.otus.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MailOutboxTest {
    private class Store :
        MemoryMapStore(),
        OutboxStore

    private val store = Store()
    private val sent = mutableListOf<Pair<Draft, SendTime>>()
    private val checks = mutableListOf<Draft>()
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
        SendChecker { draft ->
            checks += draft
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
            failure = SendFailure.Unreachable
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
            failure = SendFailure.Unreachable
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
            failure = SendFailure.Unreachable
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
    fun undoDropsAWaitingMessageButNotOneOnItsWay() =
        runTest {
            val outbox = outbox()
            assertTrue(outbox.cancel(outbox.add(draft, null, NOW + 5.seconds)))
            val sending = outbox.add(draft, null, NOW)
            failure = SendFailure.Unreachable
            outbox.send(sending)
            assertFalse(outbox.cancel(sending), "The server may already have it")
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
