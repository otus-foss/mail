package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.FakeScheduledSends
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the outbox does with its messages at the next start or in a background run. */
class OutboxResumeTest {
    private val store = MemoryMapStore()
    private val sent = mutableListOf<SendTime>()
    private var checks = 0
    private var failure: SendFailure? = null
    private var check = SendCheck.NotSent
    private var now = NOW
    private val draft = Draft("a1", subject = "Lunch", emailId = "d1")

    private val sender =
        DraftSender { _, _, time ->
            sent += time
            failure?.let { Result.failure(SendException(it)) } ?: Result.success(SentMessage("a1", "e9", "s1"))
        }

    private val checker =
        SendChecker { _, _, _ ->
            checks++
            check
        }

    /** An outbox as a new process builds it; the sender stores the copy `e9` before it submits it. */
    private fun outbox() =
        MailOutbox(
            DeviceStore(store, OutboxStore),
            sender.storingCopy("e9"),
            checker,
            FakeScheduledSends(),
            now = { now },
        )

    private fun saved() = OutboxStore.ENTRIES.of(store.saved).orEmpty()

    @Test
    fun aFailedSendThatWentAfterAllIsNotOfferedAsNotSent() =
        runTest {
            val first = outbox()
            val id = first.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.ServerProblem)
            assertTrue(first.send(id).isFailure, "A gateway error may hide a send that went")

            failure = null
            check = SendCheck.Sent
            now = NOW + 10.minutes
            val restarted = outbox()
            restarted.resume()

            assertEquals(1, checks, "The server is asked what became of the first try")
            assertTrue(restarted.offers().isEmpty(), "Open would let the user send it a second time")
            assertTrue(saved().isEmpty())
            assertEquals(1, sent.size, "Nothing goes again by itself")
        }

    @Test
    fun aFailedSendIsOfferedOnlyOnceTheServerSaysItDidNotGo() =
        runTest {
            val first = outbox()
            val id = first.add(draft, null, NOW)
            failure = SendFailure.Mail(MailFailure.ServerProblem)
            first.send(id)

            check = SendCheck.Unreachable
            now = NOW + 10.minutes
            val restarted = outbox()
            restarted.resume()
            assertTrue(restarted.offers().isEmpty(), "Nobody knows yet whether it went")
            assertTrue(restarted.waiting(), "A background run asks again")

            check = SendCheck.NotSent
            restarted.resume()
            assertEquals(OutboxProblem.Refused, restarted.offers().single().problem)
            assertEquals(1, sent.size, "It waits for the user")
        }

    private companion object {
        val NOW = Instant.parse("2026-11-25T12:00:00Z")
    }
}
