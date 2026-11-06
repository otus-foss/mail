package com.otus.mail.feature.inbox.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A refresh follows what changed in the mailboxes and lists instead of reading them again. */
class SyncFollowTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeMailServer(INBOX, email("m1", 10) + ", " + email("m2", 9))
    private val source = server.source(store, cache)

    private suspend fun loaded() = source.messages(MailScope.Inbox, MailQuery()).messages.map { it.emailId }

    @Test
    fun aRefreshAsksForMailboxChangesInsteadOfEveryMailbox() =
        runTest {
            loaded()
            val calls = server.calls.size
            loaded()
            val later = server.calls.drop(calls)
            assertTrue(later.any { it.first == "Mailbox/changes" })
            assertTrue(
                later.filter { it.first == "Mailbox/get" }.all { "ids" in it.second },
                "Only what changed is read",
            )
        }

    @Test
    fun aListTheServerCanFollowSplicesInNewMail() =
        runTest {
            server.followsQueries = true
            assertEquals(listOf("m1", "m2"), loaded())
            server.add(email("m3", 11))
            val calls = server.calls.size
            assertEquals(listOf("m3", "m1", "m2"), loaded())
            val later = server.calls.drop(calls).map { it.first }
            assertTrue("Email/queryChanges" in later)
            assertTrue("Email/query" !in later, "The list is not read again")
        }

    @Test
    fun aListTheServerCannotFollowIsReadAgain() =
        runTest {
            loaded()
            server.add(email("m3", 11))
            val calls = server.calls.size
            assertEquals(listOf("m3", "m1", "m2"), loaded())
            assertTrue(server.calls.drop(calls).none { it.first == "Email/queryChanges" })
        }

    @Test
    fun anAccountThatCannotLoadSaysWhenItsMailLastMatchedTheServer() =
        runTest {
            server.down = true
            assertNull(
                source
                    .messages(MailScope.Inbox, MailQuery())
                    .unavailable
                    .single()
                    .syncedAt,
                "Never synced yet",
            )
            server.down = false
            loaded()
            server.down = true
            val unavailable = source.messages(MailScope.Inbox, MailQuery()).unavailable.single()
            assertEquals(MailFailure.Unreachable, unavailable.reason)
            assertNotNull(unavailable.syncedAt)
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
