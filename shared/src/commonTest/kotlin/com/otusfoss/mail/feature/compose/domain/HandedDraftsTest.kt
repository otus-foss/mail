package com.otusfoss.mail.feature.compose.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.data.DeviceDrafts
import com.otusfoss.mail.core.messages.data.FakeDraftServer
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftSender
import com.otusfoss.mail.core.messages.domain.SendCancel
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.core.messages.domain.WaitingDraftStore
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.FakeScheduledSends
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A draft written offline stays in the waiting drafts or the outbox while it is sent or taken back. */
class HandedDraftsTest {
    private val server = FakeDraftServer().apply { failure = MailFailure.Unreachable }
    private val draftsMap = MemoryMapStore()
    private val outboxMap = MemoryMapStore()
    private val drafts = DeviceDrafts(server, DeviceStore(draftsMap, WaitingDraftStore), io = Dispatchers.Unconfined)
    private val gate = CompletableDeferred<Unit>()
    private var gated = false

    private val sender =
        DraftSender { _, _, _ ->
            if (gated) gate.await()
            Result.success(SentMessage("ada", "e9", "s1"))
        }

    private val outbox =
        MailOutbox(
            DeviceStore(outboxMap, OutboxStore),
            sender,
            { _, _, _ -> SendCheck.NotSent },
            FakeScheduledSends(),
            now = { NOW },
            io = Dispatchers.Unconfined,
            drafts = drafts.outbox,
        )

    private fun savedEntries() = OutboxStore.ENTRIES.of(outboxMap.saved).orEmpty()

    @Test
    fun undoOfASendPutsADraftWrittenOfflineBackOnTheDevice() =
        runTest {
            val id = drafts.save(Draft("ada", subject = "Hello")).getOrThrow()
            assertTrue(isDeviceDraftId(id))

            val entry = outbox.add(Draft("ada", subject = "Hello", emailId = id), null, due = NOW + 10.seconds)
            assertEquals(SendCancel.Stopped, outbox.cancel(entry), "Undo takes the message back")

            assertEquals(emptyList(), savedEntries(), "The outbox no longer holds it")
            assertEquals(listOf(id), drafts.list.waiting("ada").map { it.id }, "The waiting drafts hold it again")
            assertEquals("Hello", drafts.list.load(id)?.subject, "Its device id opens it")
        }

    @Test
    fun aDraftWaitsOnTheDeviceUntilTheOutboxHoldsIt() =
        runTest {
            val id = drafts.save(Draft("ada", subject = "Offline")).getOrThrow()
            val first = outbox.add(Draft("ada", subject = "First"), null, due = NOW)
            gated = true
            val sending = async(Dispatchers.Unconfined) { outbox.send(first) }

            val adding =
                async(Dispatchers.Unconfined) {
                    outbox.add(Draft("ada", subject = "Offline", emailId = id), null, due = NOW)
                }

            assertTrue(adding.isActive, "Send still waits for the outbox")
            assertEquals(listOf("First"), savedEntries().map { it.draft.subject }, "The outbox does not hold it yet")
            assertEquals(listOf(id), drafts.list.waiting("ada").map { it.id }, "The waiting drafts still hold it")

            // As when the page's scope or the process ends while the first send waits for its server.
            adding.cancel()
            gate.complete(Unit)
            sending.await()
            assertEquals(listOf(id), drafts.list.waiting("ada").map { it.id }, "Nothing was lost")
        }

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}
