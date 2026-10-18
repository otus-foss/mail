package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.FolderOutcome
import com.otus.mail.feature.folders.domain.FolderPlacement
import com.otus.mail.feature.folders.domain.FolderRepository
import com.otus.mail.feature.folders.domain.MailFolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersCacheTest {
    /** Changes are refused; only the cached and server lists matter here. */
    private class NoChanges : FolderRepository {
        override suspend fun accounts(): List<AccountFolders> = emptyList()

        override suspend fun create(
            accountId: String,
            name: String,
            parentId: String?,
        ): FolderOutcome = error("not used")

        override suspend fun rename(
            folder: MailFolder,
            name: String,
        ): FolderOutcome = error("not used")

        override suspend fun place(placement: FolderPlacement): FolderOutcome = error("not used")

        override suspend fun delete(folder: MailFolder): FolderOutcome = error("not used")
    }

    private fun folders(
        vararg ids: String,
        unread: Long = 0,
    ) = listOf(AccountFolders("ada", "Ada", ids.map { MailFolder("ada", it, it, unread = unread) }))

    @Test
    fun cachedFoldersShowWhileTheServerLoadsAndItsAnswerReplacesThem() =
        runTest {
            val server = CompletableDeferred<List<AccountFolders>>()
            var cachedReads = 0
            val state =
                FoldersState(NoChanges(), this, loadCached = {
                    cachedReads++
                    folders("M1", unread = 2)
                }) { server.await() }
            state.refresh()
            runCurrent()
            assertEquals(
                2,
                state.accounts
                    .single()
                    .folders
                    .single()
                    .unread,
            )
            assertTrue(state.loading)
            server.complete(folders("M1", "M2", unread = 5))
            runCurrent()
            assertEquals(
                listOf("M1", "M2"),
                state.accounts
                    .single()
                    .folders
                    .map { it.id },
            )
            assertFalse(state.loading)
            state.refresh()
            runCurrent()
            assertEquals(1, cachedReads, "A shown list reloads from the server only")
        }

    @Test
    fun anEmptyCacheShowsNothingBeforeTheServer() =
        runTest {
            val server = CompletableDeferred<List<AccountFolders>>()
            val state =
                FoldersState(
                    NoChanges(),
                    this,
                    loadCached = { listOf(AccountFolders("ada", "Ada")) },
                ) { server.await() }
            state.refresh()
            runCurrent()
            assertTrue(state.accounts.isEmpty())
            server.complete(folders("M1"))
            runCurrent()
            assertEquals(1, state.accounts.size)
        }
}
