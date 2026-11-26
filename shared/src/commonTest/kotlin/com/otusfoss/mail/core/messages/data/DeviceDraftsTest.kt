package com.otusfoss.mail.core.messages.data

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.DraftException
import com.otusfoss.mail.core.messages.domain.DraftFailure
import com.otusfoss.mail.core.messages.domain.DraftRepository
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.WaitingDraftStore
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DeviceDraftsTest {
    private val server = FakeDraftServer()
    private val map = MemoryMapStore()
    private var woken = 0
    private var clock = 1_000L
    private val drafts = deviceDrafts()
    private val hello = Draft("ada", subject = "Hello")

    @Test
    fun aSaveTheServerTakesKeepsNothingOnTheDevice() =
        runTest {
            assertEquals("e1", drafts.save(hello).getOrThrow())

            assertEquals(emptyList(), drafts.list.waiting("ada"))
            assertEquals(emptyMap(), map.read())
        }

    @Test
    fun anOfflineSaveCountsAsMadeAndWaitsOnTheDevice() =
        runTest {
            server.failure = MailFailure.Unreachable

            val id = drafts.save(hello).getOrThrow()

            assertTrue(isDeviceDraftId(id))
            assertEquals(listOf(hello), drafts.list.waiting("ada").map { it.draft })
            assertEquals(emptyList(), drafts.list.waiting("bob"))
            assertEquals(hello.copy(emailId = id), drafts.list.load(id))
            assertEquals(1, woken)
            // Another instance, as after a restart, still holds it.
            assertEquals(listOf(id), deviceDrafts().list.waiting("ada").map { it.id })
        }

    @Test
    fun laterOfflineSavesReplaceTheWaitingCopyAndRememberTheServersCopy() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable

            val id = drafts.save(hello.copy(subject = "Hello again", emailId = stored)).getOrThrow()
            val same = drafts.save(hello.copy(subject = "Hello once more", emailId = id)).getOrThrow()

            assertEquals(id, same)
            assertEquals(
                listOf(hello.copy(subject = "Hello once more", emailId = stored)),
                drafts.list.waiting("ada").map { it.draft },
            )
        }

    @Test
    fun aSaveOnceOnlineReplacesTheServersCopyAndStopsWaiting() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello.copy(emailId = stored)).getOrThrow()
            server.failure = null

            val saved = drafts.save(hello.copy(subject = "Online", emailId = id)).getOrThrow()

            assertEquals("e2", saved)
            assertEquals(stored, server.saves.last().emailId)
            assertEquals(emptyList(), drafts.list.waiting("ada"))
        }

    @Test
    fun aRefusedSaveFailsButKeepsWhatWasTypedInTheWaitingCopy() =
        runTest {
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.NotPermitted

            assertTrue(drafts.save(hello.copy(subject = "More", emailId = id)).isFailure)
            assertEquals(listOf("More"), drafts.list.waiting("ada").map { it.draft.subject })
            // A draft that never waited is not kept when the server refuses it.
            assertTrue(drafts.save(hello.copy(accountId = "bob")).isFailure)
            assertEquals(emptyList(), drafts.list.waiting("bob"))
        }

    @Test
    fun flushStoresTheWaitingDraftsOldestFirstAndThePageThenReplacesTheNewCopy() =
        runTest {
            server.failure = MailFailure.Unreachable
            val first = drafts.save(hello).getOrThrow()
            clock += 5
            drafts.save(hello.copy(subject = "Second")).getOrThrow()
            server.failure = null

            assertFalse(drafts.flush())

            assertEquals(listOf("Hello", "Second"), server.saves.takeLast(2).map { it.subject })
            assertEquals(emptyList(), drafts.list.waiting("ada"))
            assertEquals("e1", drafts.list.arrivedAs(first))
            // The page that wrote the first draft still holds its device id.
            drafts.save(hello.copy(subject = "Edited", emailId = first)).getOrThrow()
            assertEquals("e1", server.saves.last().emailId)
        }

    @Test
    fun flushStopsWhileOfflineAndSkipsOnlyARefusedDraft() =
        runTest {
            server.failure = MailFailure.Unreachable
            drafts.save(hello).getOrThrow()
            drafts.save(hello.copy(accountId = "bob")).getOrThrow()

            assertTrue(drafts.flush())
            assertEquals(4, server.saves.size, "Each login tries once")

            server.failure = null
            server.refused = "ada"
            assertFalse(drafts.flush(), "A refusal is no reason to try again soon")
            assertEquals(listOf("ada"), deviceDrafts().list.waiting("ada").map { it.loginId })
            assertEquals(emptyList(), drafts.list.waiting("bob"))
        }

    @Test
    fun flushOfOneLoginLeavesTheOthersWaiting() =
        runTest {
            server.failure = MailFailure.Unreachable
            drafts.save(hello).getOrThrow()
            drafts.save(hello.copy(accountId = "bob")).getOrThrow()
            server.failure = null

            assertFalse(drafts.flush("bob"))

            assertEquals(1, drafts.list.waiting("ada").size)
            assertEquals(emptyList(), drafts.list.waiting("bob"))
        }

    @Test
    fun discardingAWaitingDraftDropsItAndTheCopyItReplaced() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello.copy(emailId = stored)).getOrThrow()
            val fresh = drafts.save(hello.copy(subject = "Fresh")).getOrThrow()
            server.failure = null

            assertTrue(drafts.discard("ada", id).isSuccess)
            assertTrue(drafts.discard("ada", fresh).isSuccess)
            assertTrue(drafts.discard("ada", "e9").isSuccess)

            assertEquals(emptyList(), drafts.list.waiting("ada"))
            assertEquals(listOf("ada" to stored, "ada" to "e9"), server.discards)
        }

    @Test
    fun anOfflineDiscardDestroysTheReplacedCopyOnceTheServerAnswers() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello.copy(emailId = stored)).getOrThrow()

            assertTrue(drafts.discard("ada", id).isSuccess)
            assertTrue(drafts.discard("ada", "e9").isSuccess)

            assertNull(drafts.list.load(id))
            assertEquals(
                listOf(stored, "e9"),
                drafts.list
                    .waiting("ada")
                    .filter {
                        it.discarded
                    }.map { it.draft.emailId }
                    .sortedBy { it },
            )
            server.failure = null
            assertFalse(drafts.flush())
            assertEquals(listOf("ada" to stored, "ada" to "e9"), server.discards.takeLast(2))
            assertEquals(emptyList(), drafts.list.waiting("ada"))
        }

    @Test
    fun listsDoNotWaitForASaveThatWaitsForTheServer() =
        runTest {
            server.failure = MailFailure.Unreachable
            drafts.save(hello).getOrThrow()
            server.failure = null
            val answer = CompletableDeferred<Unit>()
            server.gate = answer

            val saving = async { drafts.save(hello.copy(subject = "Slow")) }
            testScheduler.runCurrent()

            assertEquals(1, drafts.list.waiting("ada").size)
            answer.complete(Unit)
            saving.await()
        }

    @Test
    fun aHandedOverDeviceIdStillNamesTheServersCopy() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello.copy(emailId = stored)).getOrThrow()
            drafts.handOver(hello.copy(emailId = id))
            server.failure = null

            drafts.save(hello.copy(subject = "After Undo", emailId = id)).getOrThrow()

            assertEquals(stored, server.saves.last().emailId)
        }

    @Test
    fun aCopyAnotherClientSentWhileOfflineIsNeitherReplacedNorDestroyed() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val edited = drafts.save(hello.copy(subject = "Edited", emailId = stored)).getOrThrow()
            val dropped = drafts.save(hello.copy(subject = "Dropped", emailId = "e8")).getOrThrow()
            drafts.discard("ada", dropped)
            server.sent = setOf(stored, "e8")
            server.failure = null

            assertFalse(drafts.flush())

            assertEquals(null, server.saves.last { it.subject == "Edited" }.emailId)
            assertEquals(emptyList(), server.discards)
            assertEquals(emptyList(), drafts.list.waiting("ada"))
            assertNull(drafts.list.load(edited))
        }

    @Test
    fun droppingAListedDraftRemovesItOnce() =
        runTest {
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello).getOrThrow()

            assertTrue(drafts.list.drop(id))
            assertFalse(drafts.list.drop(id))
            assertEquals(emptyList(), drafts.list.waiting("ada"))
        }

    @Test
    fun aDraftHandedOverForSendingNamesTheServersCopyAndStopsWaiting() =
        runTest {
            val stored = drafts.save(hello).getOrThrow()
            server.failure = MailFailure.Unreachable
            val id = drafts.save(hello.copy(emailId = stored)).getOrThrow()
            val fresh = drafts.save(hello.copy(subject = "Fresh")).getOrThrow()

            assertEquals(stored, drafts.handOver(hello.copy(emailId = id)).emailId)
            assertNull(drafts.handOver(hello.copy(emailId = fresh)).emailId)
            assertEquals("e7", drafts.handOver(hello.copy(emailId = "e7")).emailId)
            assertEquals(emptyList(), drafts.list.waiting("ada"))
        }

    @Test
    fun removingAnAccountDropsOnlyItsWaitingDrafts() =
        runTest {
            server.failure = MailFailure.Unreachable
            drafts.save(hello).getOrThrow()
            drafts.save(hello.copy(accountId = "bob")).getOrThrow()

            drafts.removing("ada")

            assertEquals(emptyList(), drafts.list.waiting("ada"))
            assertEquals(1, drafts.list.waiting("bob").size)
        }

    private fun deviceDrafts(): DeviceDrafts {
        var ids = 0
        return DeviceDrafts(
            server,
            DeviceStore(map, WaitingDraftStore),
            wake = { woken++ },
            now = { Instant.fromEpochMilliseconds(clock) },
            newId = { "device-draft:${map.read().size}-${ids++}" },
            io = Dispatchers.Unconfined,
        )
    }
}
