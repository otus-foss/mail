package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedPagingTest {
    private fun TestScope.loaded(source: GatedSource): InboxFeedState {
        val state = InboxFeedState(source, this)
        state.refresh()
        runCurrent()
        source.cachedReads.single().complete(snapshot())
        source.loads.single().complete(snapshot("a", "b", hasMore = true))
        runCurrent()
        return state
    }

    @Test
    fun theNextPageAddsToTheListAndEndsLoadingMore() =
        runTest {
            val source = GatedSource()
            val state = loaded(source)
            assertTrue(state.hasMore)
            state.loadMore()
            runCurrent()
            assertTrue(state.loadingMore)
            source.pages.single().complete(snapshot("a", "b", "c"))
            runCurrent()
            assertEquals(listOf("a", "b", "c"), state.messages.map { it.id })
            assertFalse(state.loadingMore)
            assertFalse(state.hasMore)
        }

    @Test
    fun aSecondCallWhileAPageLoadsAndACallWithoutMoreAreIgnored() =
        runTest {
            val source = GatedSource()
            val state = loaded(source)
            state.loadMore()
            state.loadMore()
            runCurrent()
            assertEquals(1, source.pages.size)
            source.pages.single().complete(snapshot("a", "b", "c"))
            runCurrent()
            state.loadMore()
            runCurrent()
            assertEquals(1, source.pages.size, "Nothing more is on the server")
        }

    @Test
    fun aPageThatArrivesAfterTheScopeChangedIsDropped() =
        runTest {
            val source = GatedSource()
            val state = loaded(source)
            state.loadMore()
            runCurrent()
            state.show(MailScope.Folders(setOf(MailScope.Folder("ada", "l"))))
            assertFalse(state.loadingMore)
            source.pages.single().complete(snapshot("a", "b", "c"))
            runCurrent()
            assertEquals(emptyList(), state.messages)
            source.cachedReads.last().complete(snapshot())
            source.loads.last().complete(snapshot("later"))
            runCurrent()
            assertEquals(listOf("later"), state.messages.map { it.id })
        }

    @Test
    fun aPageKeepsAMoveTheServerHasNotConfirmed() =
        runTest {
            val source = GatedSource()
            val state = loaded(source)
            state.remove("a")
            state.loadMore()
            runCurrent()
            source.pages.single().complete(snapshot("a", "b", "c"))
            runCurrent()
            assertEquals(listOf("b", "c"), state.messages.map { it.id })
        }

    @Test
    fun noPageLoadsWhileARefreshRuns() =
        runTest {
            val source = GatedSource()
            val state = loaded(source)
            state.refresh()
            state.loadMore()
            runCurrent()
            assertTrue(source.pages.isEmpty())
            source.loads.last().complete(snapshot("a", "b", hasMore = true))
            runCurrent()
        }
}
