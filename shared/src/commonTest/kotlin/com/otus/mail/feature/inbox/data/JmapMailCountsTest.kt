package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.mailbox.Mailboxes
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.connections
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class JmapMailCountsTest {
    private val server =
        FakeMailServer(
            """{"id": "a", "name": "Inbox", "role": "inbox"}, {"id": "sc", "name": "Scheduled", "role": "scheduled"}""",
            listOf("a", "a", "sc", "sc")
                .mapIndexed { index, box ->
                    """{"id": "m$index", "threadId": "t-$box", "mailboxIds": {"$box": true}, "receivedAt": "2026-11-10T0$index:00:00Z"}"""
                }.joinToString(),
        )

    private suspend fun count(scope: MailScope): Long? {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val clock =
            object : Clock {
                override fun now() = Instant.parse("2026-11-10T12:00:00Z")
            }
        val counts = JmapMailCounts(server.connections(store), Mailboxes(InMemoryMailCache()), { TimeZone.UTC }, clock)
        return counts.count(listOf(ADA_LOGIN), scope, MailQuery())
    }

    @Test
    fun scheduledCountsEmailsWhileOtherFoldersCountConversations() =
        runTest {
            assertEquals(2, count(MailScope.Role(MailboxRole.Scheduled)))
            assertEquals(1, count(MailScope.Inbox))
        }
}
