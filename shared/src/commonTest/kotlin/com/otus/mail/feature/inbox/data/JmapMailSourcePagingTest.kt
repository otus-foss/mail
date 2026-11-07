package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.DateFilter
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.at
import com.otus.mail.testing.connections
import com.otus.mail.testing.emailWriter
import com.otus.mail.testing.source
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Next pages that start right after the rows the list shows, whatever changed since. */
class JmapMailSourcePagingTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeMailServer(INBOX, (1..6).joinToString { email("m$it", 10 - it) })
    private val source = server.source(store, cache, limit = 2)

    private suspend fun shown() = source.messages(MailScope.Inbox, MailQuery()).messages.map { it.emailId }

    private suspend fun more(query: MailQuery = MailQuery()) =
        source.more(MailScope.Inbox, query).messages.map { it.emailId }

    @Test
    fun aListTheCacheLacksGetsItsFirstPageAndThenTheNext() =
        runTest {
            shown()
            val lastWeek = MailQuery(filters = InboxFilters(date = DateFilter.Last7))
            val calls = server.calls.size
            assertEquals(listOf("m1", "m2"), more(lastWeek))
            assertFalse(server.calls.drop(calls).any { it.first == "Email/changes" }, "A next page is no refresh")
            assertEquals(JsonPrimitive(0), server.calls("Email/query").last()["position"])
            assertEquals(listOf("m1", "m2", "m3", "m4"), more(lastWeek))
            val query = server.calls("Email/query").last()
            assertEquals(JsonPrimitive("m2"), query["anchor"])
            assertEquals(JsonPrimitive(1), query["anchorOffset"])
        }

    @Test
    fun aMovedRowDoesNotSkipTheNextOnes() =
        runTest {
            val first = source.messages(MailScope.Inbox, MailQuery()).messages
            val mover = emailWriter(server.connections(store), cache)
            mover.move(first.first(), "r", leaving = setOf("a"))
            assertEquals(listOf("m2", "m3", "m4"), more())
        }

    @Test
    fun aListWithNoLoadedRowLeftLoadsAgainWithAPageMore() =
        runTest {
            val mover = emailWriter(server.connections(store), cache)
            source.messages(MailScope.Inbox, MailQuery()).messages.forEach { mover.move(it, "r", leaving = setOf("a")) }
            assertEquals(listOf("m3", "m4", "m5", "m6"), more())
            val query = server.calls("Email/query").last()
            assertNull(query["anchor"], "No row is left to start after")
            assertEquals(JsonPrimitive(4), query["limit"])
        }

    @Test
    fun anAnchorTheServerNoLongerListsLoadsTheListAgain() =
        runTest {
            shown()
            // m2 leaves the inbox on the server only, so the device still starts after it.
            server.update("m2") { it.at("mailboxIds/a", JsonNull).at("mailboxIds/r", JsonPrimitive(true)) }
            assertEquals(listOf("m1", "m3", "m4", "m5"), more())
            val (anchored, reloaded) = server.calls("Email/query").takeLast(2)
            assertEquals(JsonPrimitive("m2"), anchored["anchor"])
            assertEquals(JsonPrimitive(4), reloaded["limit"])
        }

    private companion object {
        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}"""

        fun email(
            id: String,
            hour: Int,
        ) = """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T${hour.toString().padStart(2, '0')}:00:00Z"}"""
    }
}
