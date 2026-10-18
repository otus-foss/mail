package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.feature.inbox.domain.InboxFailure
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.UnavailableAccount
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The inbox list kept on the device, synced by changes, and loaded a page at a time. */
class JmapMailSourceCacheTest {
    private val login = JmapLogin("ada", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
    private val store = InMemoryLoginStore().apply { save(login, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeMailServer(INBOX, email("m1", 10) + ", " + email("m2", 9))
    private val source = server.source(store, cache)
    private val query = MailQuery()

    private suspend fun loaded() = source.messages(MailScope.Inbox, query).messages.map { it.emailId }

    /** The ids each new `Email/get` of rows asked for since [from] calls. */
    private fun rowFetches(from: Int) =
        server.calls
            .drop(from)
            .filter { (name, arguments) -> name == "Email/get" && "preview" in arguments.properties }
            .flatMap { (it.second["ids"] as JsonArray).map { id -> (id as JsonPrimitive).content } }
            .toSet()

    @Test
    fun cachedShowsTheLastLoadWithoutTheServer() =
        runTest {
            assertTrue(
                server
                    .source(store, cache)
                    .cached(MailScope.Inbox, query)
                    .messages
                    .isEmpty(),
            )
            val shown = source.messages(MailScope.Inbox, query).messages
            server.down = true
            val calls = server.calls.size
            val restarted = server.source(store, cache).cached(MailScope.Inbox, query)
            assertEquals(shown, restarted.messages)
            assertEquals(calls, server.calls.size, "Nothing is asked of the server")
        }

    @Test
    fun anUnchangedRefreshReadsNoEmails() =
        runTest {
            loaded()
            val requests = server.bodies.size
            val calls = server.calls.size
            assertEquals(listOf("m1", "m2"), loaded())
            assertEquals(emptySet(), rowFetches(calls))
            assertEquals(2, server.bodies.size - requests, "Changes with the mailboxes, then the list")
            assertTrue(server.calls.drop(calls).any { it.first == "Email/changes" })
        }

    @Test
    fun changesPatchTheList() =
        runTest {
            loaded()
            val calls = server.calls.size
            server.update("m1") { JsonObject(it + ("keywords" to JsonObject(mapOf(SEEN to JsonPrimitive(true))))) }
            server.destroy("m2")
            server.add(email("m3", 11))
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf("m3", "m1"), messages.map { it.emailId })
            assertFalse(messages.last().attributes.unread)
            assertEquals(setOf("m1", "m3"), rowFetches(calls), "Only new and changed rows are read")
        }

    @Test
    fun forgottenStatesLoadEverythingAgain() =
        runTest {
            loaded()
            val calls = server.calls.size
            server.forgotStates = true
            server.update("m2") { JsonObject(it + ("keywords" to JsonObject(mapOf(SEEN to JsonPrimitive(true))))) }
            val messages = source.messages(MailScope.Inbox, query).messages
            assertEquals(listOf(true, false), messages.map { it.attributes.unread })
            assertEquals(setOf("m1", "m2"), rowFetches(calls))
        }

    @Test
    fun moreAppendsTheNextPageUntilTheEnd() =
        runTest {
            val server = FakeMailServer(INBOX, (1..5).joinToString { email("m$it", 10 - it) })
            val source = server.source(store, cache, limit = 2)
            val first = source.messages(MailScope.Inbox, query)
            assertEquals(listOf("m1", "m2"), first.messages.map { it.emailId })
            assertTrue(first.hasMore)
            val second = source.more(MailScope.Inbox, query).messages.map { it.emailId }
            assertEquals(listOf("m1", "m2", "m3", "m4"), second)
            val last = source.more(MailScope.Inbox, query)
            assertEquals(5, last.messages.size)
            assertFalse(last.hasMore)
            val refreshed = source.messages(MailScope.Inbox, query)
            assertEquals(5, refreshed.messages.size, "A refresh keeps the pages loaded so far")
            assertEquals(JsonPrimitive(5), server.calls("Email/query").last()["limit"])
        }

    @Test
    fun anUnreachableLoginKeepsItsCachedRows() =
        runTest {
            val shown = source.messages(MailScope.Inbox, query).messages
            server.down = true
            val snapshot = source.messages(MailScope.Inbox, query)
            assertEquals(shown, snapshot.messages)
            assertEquals(listOf(UnavailableAccount("Ada", InboxFailure.Unreachable)), snapshot.unavailable)
        }

    private val JsonObject.properties: List<String>
        get() = (get("properties") as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty()

    private companion object {
        const val SEEN = "\$seen"
        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "r", "name": "Receipts"}"""

        fun email(
            id: String,
            hour: Int,
        ) = """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T${hour.toString().padStart(2, '0')}:00:00Z"}"""
    }
}
