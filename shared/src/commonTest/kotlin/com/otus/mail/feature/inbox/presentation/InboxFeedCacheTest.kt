package com.otus.mail.feature.inbox.presentation

import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.InboxStorage
import com.otus.mail.feature.inbox.domain.InboxStorageException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedCacheTest {
    @Test
    fun theDevicesCopyShowsWhileLoadingAndTheServersReplacesIt() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            source.cachedReads.single().complete(snapshot("kept"))
            runCurrent()
            assertEquals(listOf("kept"), state.messages.map { it.id })
            assertTrue(state.loading, "The server has not answered yet")
            source.loads.single().complete(snapshot("new", "kept"))
            runCurrent()
            assertEquals(listOf("new", "kept"), state.messages.map { it.id })
            assertFalse(state.loading)
        }

    @Test
    fun aSlowDeviceReadNeverReplacesTheServersMail() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            source.loads.single().complete(snapshot("new"))
            runCurrent()
            source.cachedReads.single().complete(snapshot("old"))
            runCurrent()
            assertEquals(listOf("new"), state.messages.map { it.id })
        }

    @Test
    fun aRefreshOfAShownListSkipsTheDevicesCopy() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            source.cachedReads.single().complete(snapshot())
            source.loads.single().complete(snapshot("a"))
            runCurrent()
            state.refresh()
            runCurrent()
            assertEquals(1, source.cachedReads.size)
            assertEquals(listOf("a"), state.messages.map { it.id }, "The shown mail stays while the server answers")
            source.loads.last().complete(snapshot("a"))
            runCurrent()
        }

    @Test
    fun aNewScopeShowsItsOwnCopyAndNotTheOldScopesLateRead() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            state.show(MailScope.Folders(setOf(MailScope.Folder("ada", "l"))))
            runCurrent()
            source.cachedReads[0].complete(snapshot("inbox"))
            source.cachedReads[1].complete(snapshot("later"))
            runCurrent()
            assertEquals(listOf("later"), state.messages.map { it.id })
            source.loads.forEach { it.complete(snapshot("later")) }
            runCurrent()
        }

    @Test
    fun aFailedCacheWriteKeepsTheListAndAsksForARefresh() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            source.cachedReads.single().complete(snapshot("kept"))
            source.loads.single().completeExceptionally(
                InboxStorageException(IllegalStateException("disk full"), InboxStorage.Cache),
            )
            runCurrent()
            assertEquals(listOf("kept"), state.messages.map { it.id })
            assertEquals("Couldn't save cached mail. Refresh to try again.", state.notice)
            assertFalse(state.loading)
        }
}
