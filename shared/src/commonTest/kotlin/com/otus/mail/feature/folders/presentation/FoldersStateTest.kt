package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.MailFolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersStateTest {
    /** Every change works, and a new folder gets the id M9. The list comes from [SlowLoads]. */
    private class FakeFolders : FolderRepository {
        override suspend fun accounts(): List<AccountFolders> = emptyList()

        override suspend fun create(
            accountId: String,
            name: String,
            parentId: String?,
        ): FolderOutcome = FolderOutcome.Done("M9")

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ): FolderOutcome = FolderOutcome.Done(folder.id)

        override suspend fun place(placement: FolderPlacement): FolderOutcome = FolderOutcome.Done(placement.folder.id)

        override suspend fun delete(folder: MailFolder): FolderOutcome = FolderOutcome.Done(folder.id)
    }

    /**
     * Each load waits for its own entry in [answers] and, like a request already sent, finishes
     * when replaced. Tests answer every load before asserting, so a failure cannot leave one waiting.
     */
    private class SlowLoads {
        val answers = mutableListOf<CompletableDeferred<List<AccountFolders>>>()

        suspend fun load(): List<AccountFolders> {
            val answer = CompletableDeferred<List<AccountFolders>>()
            answers += answer
            return withContext(NonCancellable) { answer.await() }
        }
    }

    private fun folders(
        vararg ids: String,
        unread: Long = 0,
    ) = listOf(AccountFolders("ada", "Ada", ids.map { MailFolder("ada", it, it, unread = unread) }))

    private fun FoldersState.folderIds() = accounts.single().folders.map { it.id }

    private fun TestScope.loaded(loads: SlowLoads): FoldersState {
        val state = FoldersState(FakeFolders(), this, loadAccounts = { loads.load() })
        state.refresh()
        runCurrent()
        loads.answers.single().complete(folders("M1"))
        runCurrent()
        return state
    }

    @Test
    fun aReplacedLoadNeitherShowsItsFoldersNorEndsLoading() =
        runTest {
            val loads = SlowLoads()
            val state = FoldersState(FakeFolders(), this, loadAccounts = { loads.load() })
            state.refresh()
            runCurrent()
            state.refresh()
            runCurrent()
            loads.answers[0].complete(folders("M1"))
            runCurrent()
            val loadingAfterReplaced = state.loading
            val shownAfterReplaced = state.accounts
            loads.answers[1].complete(folders("M1", "M2"))
            runCurrent()
            assertTrue(loadingAfterReplaced, "The newest load still ran")
            assertTrue(shownAfterReplaced.isEmpty())
            assertFalse(state.loading)
            assertEquals(listOf("M1", "M2"), state.folderIds())
        }

    @Test
    fun aRefreshDuringAChangeKeepsTheNewerCounts() =
        runTest {
            val loads = SlowLoads()
            val state = loaded(loads)
            state.rename(assertNotNull(state.folder("ada", "M1")), "Receipts")
            runCurrent()
            // A read mark refreshes the counts while the change still loads the folders.
            state.refresh()
            runCurrent()
            loads.answers[2].complete(folders("M1", unread = 3))
            runCurrent()
            loads.answers[1].complete(folders("M1"))
            runCurrent()
            assertEquals(3, state.folder("ada", "M1")?.unread)
            assertFalse(state.busy)
            assertFalse(state.loading)
        }

    @Test
    fun aLoadStartedBeforeAChangeCannotHideItsResult() =
        runTest {
            val loads = SlowLoads()
            val state = loaded(loads)
            state.refresh()
            runCurrent()
            var created: String? = null
            state.create("ada", "Receipts", null) { created = it }
            runCurrent()
            loads.answers[2].complete(folders("M1", "M9"))
            runCurrent()
            val createdFirst = created
            // This load read the folders before the change.
            loads.answers[1].complete(folders("M1"))
            runCurrent()
            assertEquals("M9", createdFirst)
            assertEquals(listOf("M1", "M9"), state.folderIds())
            assertFalse(state.loading)
        }

    @Test
    fun aNewFolderIsReportedOnceTheNewestLoadShowsIt() =
        runTest {
            val loads = SlowLoads()
            val state = loaded(loads)
            var created: String? = null
            state.create("ada", "Receipts", null) { created = it }
            runCurrent()
            state.refresh()
            runCurrent()
            loads.answers[1].complete(folders("M1", "M9"))
            runCurrent()
            val createdAfterReplaced = created
            val busyAfterReplaced = state.busy
            loads.answers[2].complete(folders("M1", "M9"))
            runCurrent()
            assertNull(createdAfterReplaced, "The replaced load shows nothing, so the list lacks the folder")
            assertTrue(busyAfterReplaced)
            assertEquals("M9", created)
            assertEquals(listOf("M1", "M9"), state.folderIds())
            assertFalse(state.busy)
        }
}
