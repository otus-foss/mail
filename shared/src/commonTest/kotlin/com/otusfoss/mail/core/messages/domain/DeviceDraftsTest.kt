package com.otusfoss.mail.core.messages.domain

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.storage.DeviceStore
import com.otusfoss.mail.testing.MemoryMapStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DeviceDraftsTest {
    private val server = Server()
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
            assertEquals(3, server.saves.size)

            server.failure = null
            server.refused = "ada"
            assertTrue(drafts.flush())
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

            assertTrue(drafts.flush("bob"))

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

    private class Server : DraftRepository {
        val saves = mutableListOf<Draft>()
        val discards = mutableListOf<Pair<String, String>>()
        var failure: MailFailure? = null
        var refused: String? = null
        private var stored = 0

        override suspend fun identities(accountId: String): List<SendIdentity> = emptyList()

        override suspend fun save(draft: Draft): Result<String> {
            saves += draft
            val failed = failure ?: MailFailure.NotPermitted.takeIf { draft.accountId == refused }
            if (failed != null) return Result.failure(DraftException(DraftFailure.Mail(failed)))
            return Result.success("e${++stored}")
        }

        override suspend fun discard(
            accountId: String,
            emailId: String,
        ): Result<Unit> {
            discards += accountId to emailId
            return Result.success(Unit)
        }
    }
}
