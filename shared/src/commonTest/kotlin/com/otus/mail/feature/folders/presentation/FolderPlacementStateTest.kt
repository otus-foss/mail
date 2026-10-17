package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderFailure
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.folderTree
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

/** Dragged folders show at once, go back when the server refuses, and survive older loads. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderPlacementStateTest {
    /** Each placement waits for its entry in [answers]. */
    private class Placements : FolderRepository {
        val sent = mutableListOf<FolderPlacement>()
        val answers = mutableListOf<CompletableDeferred<FolderOutcome>>()

        override suspend fun accounts(): List<AccountFolders> = emptyList()

        override suspend fun create(
            accountId: String,
            name: String,
            parentId: String?,
        ) = error("unused")

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ) = error("unused")

        override suspend fun place(placement: FolderPlacement): FolderOutcome {
            sent += placement
            return CompletableDeferred<FolderOutcome>().also { answers += it }.await()
        }

        override suspend fun delete(folder: MailFolder) = error("unused")
    }

    private val start = listOf(account("A" to 10L, "B" to 20L, "C" to 30L))
    private val placements = Placements()
    private val loads = mutableListOf<CompletableDeferred<List<AccountFolders>>>()

    private fun account(vararg orders: Pair<String, Long>) =
        AccountFolders("ada", "Ada", orders.map { (id, order) -> MailFolder("ada", id, id, sortOrder = order) })

    private fun FoldersState.order() = accounts.single().folderTree().map { it.folder.id }

    private fun TestScope.loaded(): FoldersState {
        val state =
            FoldersState(placements, this) {
                val answer = CompletableDeferred<List<AccountFolders>>().also { loads += it }
                withContext(NonCancellable) { answer.await() }
            }
        state.refresh()
        runCurrent()
        loads.single().complete(start)
        runCurrent()
        return state
    }

    @Test
    fun aDropShowsAtOnceAndSavesOnePlacement() =
        runTest {
            val state = loaded()
            state.place(assertNotNull(state.folder("ada", "C")), null, 0)
            runCurrent()
            assertEquals(listOf("C", "A", "B"), state.order())
            assertEquals(mapOf("C" to 5L), placements.sent.single().sortOrders)
            placements.answers.single().complete(FolderOutcome.Done("C"))
            runCurrent()
            loads.last().complete(listOf(account("C" to 5L, "A" to 10L, "B" to 20L)))
            runCurrent()
            assertEquals(listOf("C", "A", "B"), state.order())
            assertFalse(state.busy)
        }

    @Test
    fun aRefusedDropPutsTheListBack() =
        runTest {
            val state = loaded()
            state.place(assertNotNull(state.folder("ada", "C")), null, 0)
            runCurrent()
            placements.answers.single().complete(FolderOutcome.Failed(FolderFailure.NotPermitted))
            runCurrent()
            assertEquals(listOf("A", "B", "C"), state.order())
            assertEquals(FolderFailure.NotPermitted.message(), state.notice)
            loads.last().complete(start)
            runCurrent()
        }

    @Test
    fun aLoadStartedBeforeTheDropCannotUndoIt() =
        runTest {
            val state = loaded()
            state.refresh()
            runCurrent()
            state.place(assertNotNull(state.folder("ada", "A")), null)
            runCurrent()
            loads[1].complete(start)
            runCurrent()
            assertEquals(listOf("B", "C", "A"), state.order())
            assertFalse(state.loading)
            placements.answers.single().complete(FolderOutcome.Done("A"))
            runCurrent()
            loads.last().complete(listOf(account("B" to 20L, "C" to 30L, "A" to 40L)))
            runCurrent()
        }
}
