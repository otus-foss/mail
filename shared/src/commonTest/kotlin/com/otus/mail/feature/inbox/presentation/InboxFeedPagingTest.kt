package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.QuickFacet
import com.otus.mail.feature.inbox.domain.ReadStatus
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
    fun aListTheDeviceNarrowsLoadsAFewPagesWithoutScrolling() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.query = MailQuery(filters = InboxFilters(quick = setOf(QuickFacet.NeedsMyReply)))
            runCurrent()
            source.cachedReads.single().complete(snapshot("a"))
            source.loads.single().complete(snapshot("a", "b", hasMore = true))
            runCurrent()
            assertTrue(state.loadingMore, "The next page loads on its own")
            source.pages.single().complete(snapshot("a", "b", "c", hasMore = true))
            runCurrent()
            source.pages.last().complete(snapshot("a", "b", "c", "d"))
            runCurrent()
            assertEquals(2, source.pages.size)
            assertEquals(listOf("a", "b", "c", "d"), state.messages.map { it.id })
            assertFalse(state.loadingMore)
        }

    @Test
    fun pagesLoadOnTheirOwnOnlyUpToTheCap() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.query = MailQuery(filters = InboxFilters(quick = setOf(QuickFacet.Conversations)))
            runCurrent()
            source.cachedReads.single().complete(snapshot())
            source.loads.single().complete(snapshot("a", hasMore = true))
            runCurrent()
            repeat(times = 10) {
                val page = source.pages.last()
                if (page.isActive) page.complete(snapshot("a", hasMore = true))
                runCurrent()
            }
            assertEquals(4, source.pages.size, "Further pages wait for scrolling")
            assertFalse(state.loadingMore)
            state.loadMore()
            runCurrent()
            assertEquals(5, source.pages.size, "Scrolling still loads the next page")
            source.pages.last().complete(snapshot("a", hasMore = true))
            runCurrent()
            assertEquals(5, source.pages.size)
        }

    @Test
    fun aListTheServerFiltersWaitsForScrolling() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.query = MailQuery(filters = InboxFilters(account = "ada", status = ReadStatus.Unread))
            runCurrent()
            source.cachedReads.single().complete(snapshot())
            source.loads.single().complete(snapshot("a", "b", hasMore = true))
            runCurrent()
            assertTrue(source.pages.isEmpty())
            assertTrue(state.hasMore)
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
