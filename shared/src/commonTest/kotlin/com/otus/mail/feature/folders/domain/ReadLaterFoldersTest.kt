package com.otus.mail.feature.folders.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadLaterFoldersTest {
    private val inbox = MailFolder("ada", "i", "Inbox", role = INBOX_ROLE)

    /**
     * Folders kept in memory. Loading takes 10 ms and the server applies a new folder after 20 ms,
     * on [server], even if the app stops waiting for the reply. [refused] accounts get no folder.
     */
    private class FakeFolders(
        var accounts: List<AccountFolders>,
        private val server: CoroutineScope,
        private val refused: Set<String> = emptySet(),
    ) : FolderRepository {
        val created = mutableListOf<Pair<String, String?>>()

        override suspend fun accounts(): List<AccountFolders> {
            delay(10)
            return accounts
        }

        override suspend fun create(
            accountId: String,
            name: String,
            parentId: String?,
        ): FolderOutcome {
            val applied =
                server.async {
                    delay(20)
                    if (accountId in refused) return@async null
                    created += name to parentId
                    "n${created.size}".also { id ->
                        accounts =
                            accounts.map { account ->
                                if (account.accountId != accountId) {
                                    account
                                } else {
                                    account.copy(folders = account.folders + MailFolder(accountId, id, name, parentId))
                                }
                            }
                    }
                }
            return applied.await()?.let(FolderOutcome::Done) ?: FolderOutcome.Failed(FolderFailure.Invalid)
        }

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ) = error("unused")

        override suspend fun place(placement: FolderPlacement) = error("unused")

        override suspend fun delete(folder: MailFolder) = error("unused")
    }

    private class MemoryStore(
        var saved: Map<String, String> = emptyMap(),
    ) : ReadLaterStore {
        override fun read() = saved

        override fun write(folders: Map<String, String>) {
            saved = folders
        }
    }

    private fun ada(vararg folders: MailFolder) = AccountFolders("ada", "Ada", listOf(inbox, *folders))

    @Test
    fun offLoadsFoldersWithoutCreatingOrMarkingOne() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val accounts = ReadLaterFolders(repository).accounts { false }
            assertNull(accounts.single().readLaterId)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun createsAMissingFolderAtTheTopLevelAndSavesIt() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val store = MemoryStore()
            val accounts = ReadLaterFolders(repository, store).accounts { true }
            assertEquals(listOf<Pair<String, String?>>(READ_LATER_NAME to null), repository.created)
            assertEquals("n1", accounts.single().readLaterId)
            assertEquals(mapOf("ada" to "n1"), store.saved)
        }

    @Test
    fun keepsTheSavedFolderAfterARenameAndAMove() =
        runTest {
            val projects = MailFolder("ada", "p", "Projects")
            val renamed = MailFolder("ada", "x", "Someday", parentId = "p")
            val other = MailFolder("ada", "y", "Read later")
            val repository = FakeFolders(listOf(ada(projects, renamed, other)), backgroundScope)
            val accounts = ReadLaterFolders(repository, MemoryStore(mapOf("ada" to "x"))).accounts { true }
            assertEquals("x", accounts.single().readLaterId)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun replacesASavedFolderThatWasMovedIntoTrash() =
        runTest {
            val trash = MailFolder("ada", "t", "Trash", role = TRASH_ROLE)
            val trashed = MailFolder("ada", "x", "Read later", parentId = "t")
            val repository = FakeFolders(listOf(ada(trash, trashed)), backgroundScope)
            val store = MemoryStore(mapOf("ada" to "x"))
            val accounts = ReadLaterFolders(repository, store).accounts { true }
            assertEquals("n1", accounts.single().readLaterId)
            assertEquals(mapOf("ada" to "n1"), store.saved)
        }

    @Test
    fun replacesASavedFolderThatBecameASystemFolder() =
        runTest {
            val archive = MailFolder("ada", "x", "Archive", role = ARCHIVE_ROLE)
            val repository = FakeFolders(listOf(ada(archive)), backgroundScope)
            val accounts = ReadLaterFolders(repository, MemoryStore(mapOf("ada" to "x"))).accounts { true }
            assertEquals("n1", accounts.single().readLaterId)
        }

    @Test
    fun adoptsATopLevelFolderNamedReadLaterInAnyCase() =
        runTest {
            val nested = MailFolder("ada", "n", "Read later", parentId = "i")
            val top = MailFolder("ada", "t", "READ LATER")
            val repository = FakeFolders(listOf(ada(nested, top)), backgroundScope)
            val store = MemoryStore()
            val accounts = ReadLaterFolders(repository, store).accounts { true }
            assertEquals("t", accounts.single().readLaterId)
            assertEquals(mapOf("ada" to "t"), store.saved)
        }

    @Test
    fun leavesAccountsAloneThatFailedOrCannotCreateTopLevelFolders() =
        runTest {
            val failed = AccountFolders("bob", "Bob", failure = FolderFailure.Unreachable)
            val limited = ada().copy(limits = FolderLimits(mayCreateTopLevel = false))
            val repository = FakeFolders(listOf(limited, failed), backgroundScope)
            val store = MemoryStore(mapOf("bob" to "b", "gone" to "g"))
            val accounts = ReadLaterFolders(repository, store).accounts { true }
            assertTrue(repository.created.isEmpty())
            assertEquals(listOf(null, null), accounts.map { it.readLaterId })
            // Bob's folder is kept for when his account loads; the removed account is forgotten.
            assertEquals(mapOf("bob" to "b"), store.saved)
        }

    @Test
    fun anAccountThatCannotCreateTopLevelFoldersUsesOneItHas() =
        runTest {
            val folder = MailFolder("ada", "l", "Read later")
            val limited = ada(folder).copy(limits = FolderLimits(mayCreateTopLevel = false))
            val accounts = ReadLaterFolders(FakeFolders(listOf(limited), backgroundScope)).accounts { true }
            assertEquals("l", accounts.single().readLaterId)
        }

    @Test
    fun overlappingLoadsCreateOneFolder() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val readLater = ReadLaterFolders(repository)
            val loads = List(2) { async { readLater.accounts { true } } }.awaitAll()
            assertEquals(1, repository.created.size)
            assertEquals(listOf("n1", "n1"), loads.map { it.single().readLaterId })
        }

    @Test
    fun aReplacedLoadStillFinishesTheFolderItStartedCreating() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val readLater = ReadLaterFolders(repository)
            val first = launch { readLater.accounts { true } }
            // The first load has asked the server for a folder and is waiting for the reply.
            testScheduler.advanceTimeBy(15)
            runCurrent()
            first.cancel()
            val second = readLater.accounts { true }
            advanceUntilIdle()
            assertEquals(1, repository.created.size)
            assertEquals("n1", second.single().readLaterId)
        }

    @Test
    fun aReplacedLoadSavesTheFolderItCreated() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val store = MemoryStore()
            val readLater = ReadLaterFolders(repository, store)
            val first = launch { readLater.accounts { true } }
            testScheduler.advanceTimeBy(15)
            runCurrent()
            first.cancel()
            first.join()
            assertEquals(mapOf("ada" to "n1"), store.saved)
            // Renamed elsewhere before the next load, the folder is still found by its id.
            repository.accounts = listOf(ada(MailFolder("ada", "n1", "Someday")))
            assertEquals("n1", readLater.accounts { true }.single().readLaterId)
            assertEquals(1, repository.created.size)
        }

    @Test
    fun aReplacedLoadShowsNothing() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope, refused = setOf("ada"))
            var shown: List<AccountFolders>? = null
            val first = launch { shown = ReadLaterFolders(repository).accounts { true } }
            // Replaced while it waits for a create the server will refuse.
            testScheduler.advanceTimeBy(15)
            runCurrent()
            first.cancel()
            first.join()
            assertNull(shown)
        }

    @Test
    fun turningTheSwitchOffDuringALoadShowsNoFolder() =
        runTest {
            val repository = FakeFolders(listOf(ada(MailFolder("ada", "l", "Read later"))), backgroundScope)
            var on = true
            val load = async { ReadLaterFolders(repository).accounts { on } }
            // Turned off while the folders load.
            testScheduler.advanceTimeBy(5)
            runCurrent()
            on = false
            assertNull(load.await().single().readLaterId)
        }
}
