package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.mailbox.Mailboxes
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.connections
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
