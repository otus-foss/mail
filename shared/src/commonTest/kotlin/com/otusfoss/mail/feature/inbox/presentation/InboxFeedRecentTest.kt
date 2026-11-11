package com.otusfoss.mail.feature.inbox.presentation

import com.otusfoss.mail.core.messages.domain.ReadStatus
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFeedRecentTest {
    @Test
    fun removingAFilterShowsTheListFromBeforeAtOnce() =
        runTest {
            val source = GatedSource()
            val state = InboxFeedState(source, this)
            state.refresh()
            runCurrent()
            source.cachedReads.single().complete(snapshot())
            source.loads.single().complete(snapshot("a", "b", "c"))
            runCurrent()
            state.query = MailQuery(filters = InboxFilters(status = ReadStatus.Unread))
            runCurrent()
            source.cachedReads.last().complete(snapshot("a"))
            source.loads.last().complete(snapshot("a"))
            runCurrent()
            state.remove("c")
            state.query = MailQuery()
            assertEquals(listOf("a", "b"), state.messages.map { it.id }, "Shown before any read, without the moved row")
            runCurrent()
            assertEquals(2, source.cachedReads.size, "The device's copy is not read again")
            source.loads.last().complete(snapshot("a", "b"))
            runCurrent()
            assertEquals(listOf("a", "b"), state.messages.map { it.id })
        }
}
