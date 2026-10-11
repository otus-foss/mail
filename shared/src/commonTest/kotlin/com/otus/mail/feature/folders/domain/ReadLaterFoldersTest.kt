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
     * on [server], even if the app stops waiting for the reply.
     */
    private class FakeFolders(
        var accounts: List<AccountFolders>,
        private val server: CoroutineScope,
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
            return FolderOutcome.Done(applied.await())
        }

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ) = error("unused")

        override suspend fun move(
            folder: MailFolder,
            parentId: String?,
        ) = error("unused")

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
            val accounts = ReadLaterFolders(repository) { false }.accounts()
            assertNull(accounts.single().readLaterId)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun createsAMissingFolderAtTheTopLevelAndSavesIt() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val store = MemoryStore()
            val accounts = ReadLaterFolders(repository, store) { true }.accounts()
            assertEquals(listOf<Pair<String, String?>>(READ_LATER_NAME to null), repository.created)
            assertEquals("n1", accounts.single().readLaterId)
            assertEquals(mapOf("ada" to "n1"), store.saved)
        }

    @Test
    fun keepsTheSavedFolderAfterARename() =
        runTest {
            val renamed = MailFolder("ada", "x", "Someday")
            val repository = FakeFolders(listOf(ada(renamed, MailFolder("ada", "y", "Read later"))), backgroundScope)
            val accounts = ReadLaterFolders(repository, MemoryStore(mapOf("ada" to "x"))) { true }.accounts()
            assertEquals("x", accounts.single().readLaterId)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun adoptsATopLevelFolderNamedReadLaterInAnyCase() =
        runTest {
            val nested = MailFolder("ada", "n", "Read later", parentId = "i")
            val top = MailFolder("ada", "t", "READ LATER")
            val repository = FakeFolders(listOf(ada(nested, top)), backgroundScope)
            val store = MemoryStore()
            val accounts = ReadLaterFolders(repository, store) { true }.accounts()
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
            val accounts = ReadLaterFolders(repository, store) { true }.accounts()
            assertTrue(repository.created.isEmpty())
            assertEquals(listOf(null, null), accounts.map { it.readLaterId })
            // Bob's folder is kept for when his account loads; the removed account is forgotten.
            assertEquals(mapOf("bob" to "b"), store.saved)
        }

    @Test
    fun overlappingLoadsCreateOneFolder() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val readLater = ReadLaterFolders(repository) { true }
            val loads = List(2) { async { readLater.accounts() } }.awaitAll()
            assertEquals(1, repository.created.size)
            assertEquals(listOf("n1", "n1"), loads.map { it.single().readLaterId })
        }

    @Test
    fun aReplacedLoadStillFinishesTheFolderItStartedCreating() =
        runTest {
            val repository = FakeFolders(listOf(ada()), backgroundScope)
            val readLater = ReadLaterFolders(repository) { true }
            val first = launch { readLater.accounts() }
            // The first load has asked the server for a folder and is waiting for the reply.
            testScheduler.advanceTimeBy(15)
            runCurrent()
            first.cancel()
            val second = readLater.accounts()
            advanceUntilIdle()
            assertEquals(1, repository.created.size)
            assertEquals("n1", second.single().readLaterId)
        }
}
