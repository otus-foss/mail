package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.emailWriter
import com.otusfoss.mail.testing.mailSource
import com.otusfoss.mail.testing.stalwartLike
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Marks and moves that reach the cache at once, and lists of two accounts loaded a page at a time. */
class MailCacheWritesTest {
    private val ada = ADA_LOGIN
    private val work = JmapLogin("work", "https://work.example.org/.well-known/jmap", "ada@example.org", "Work")
    private val store =
        InMemoryLoginStore().apply {
            save(ada, "secret")
            save(work, "secret")
        }
    private val cache = InMemoryMailCache()
    private val home = FakeMailServer(INBOX, email("h1", 10) + ", " + email("h2", 8) + ", " + email("h3", 6))
    private val office = FakeMailServer(INBOX, email("w1", 9) + ", " + email("w2", 5))

    /** Each login's host reaches its own server. */
    private val connections: JmapConnections
        get() {
            val engines =
                mapOf("mail.example.org" to home, "work.example.org" to office)
                    .mapValues { stalwartLike(api = it.value::answer) }
            val routed =
                MockEngine { request ->
                    engines
                        .getValue(request.url.host)
                        .config.requestHandlers
                        .single()(this, request)
                }
            return JmapConnections(JmapClient(routed), store)
        }

    private fun source(limit: Int = 50) = mailSource(connections, cache, limit, zone = { TimeZone.UTC })

    @Test
    fun rowsPastWhereAnotherAccountStopsWaitForTheNextPage() =
        runTest {
            val source = source(limit = 2)
            val first = source.messages(MailScope.Inbox, MailQuery())
            val shown = first.messages.map { it.emailId }
            assertEquals(listOf("h1", "h2", "w1"), shown, "w2 is older than h2, where Ada's list stops")
            assertTrue(first.hasMore)
            val all = source.more(MailScope.Inbox, MailQuery())
            assertEquals(setOf("h1", "h2", "h3", "w1", "w2"), all.messages.map { it.emailId }.toSet())
            assertFalse(all.hasMore)
        }

    @Test
    fun marksAndMovesChangeTheCachedRows() =
        runTest {
            val source = source()
            val rows = source.messages(MailScope.Inbox, MailQuery()).messages.associateBy { it.emailId }
            val writer = emailWriter(connections, cache)
            assertEquals(setOf("ada/h1"), writer.mark(listOf(rows.getValue("h1")), MessageFlag.Read, on = true))
            assertTrue(writer.move(rows.getValue("w1"), "r", leaving = setOf("a")))
            home.down = true
            office.down = true
            val cached = source.cached(MailScope.Inbox, MailQuery()).messages.associateBy { it.emailId }
            assertFalse(cached.getValue("h1").attributes.unread, "The mark shows before the next sync")
            assertTrue("w1" !in cached, "The moved message has left the cached inbox")
            assertTrue(cached.getValue("h2").attributes.unread)
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
