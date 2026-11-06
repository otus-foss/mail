package com.otus.mail.core.messages.data

import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.MailQuery
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.FakeMailServer
import com.otus.mail.testing.connections
import com.otus.mail.testing.emailWriter
import com.otus.mail.testing.source
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

/** A sync and the marks made while it runs take turns, so a confirmed mark never shows reverted. */
class QueueSyncTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val server = FakeMailServer(INBOX, EMAIL)
    private val queue = EmailChangeQueue(server.connections(store), cache, store = null)
    private val source = server.source(store, cache, queue = queue)
    private val writer = emailWriter(server.connections(store), cache, queue)

    @Test
    fun aMarkMadeDuringASyncWaitsForItAndShowsAfterIt() =
        runTest {
            val message = source.messages(MailScope.Inbox, MailQuery()).messages.single()
            val reading = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val sync =
                async {
                    queue.syncing(ADA_LOGIN.id) {
                        reading.complete(Unit)
                        release.await()
                    }
                }
            reading.await()
            val mark = async { writer.mark(listOf(message), MessageFlag.Read, on = true) }
            testScheduler.runCurrent()
            assertFalse(mark.isCompleted, "The mark waits for the sync")

            release.complete(Unit)
            sync.await()
            mark.await()
            val shown = source.cached(MailScope.Inbox, MailQuery()).messages.single()
            assertFalse(shown.attributes.unread)
        }

    private companion object {
        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}"""
        const val EMAIL = """{"id": "m1", "threadId": "t-m1", "subject": "Hi", "mailboxIds": {"a": true},
            "keywords": {}, "receivedAt": "2026-10-18T10:00:00Z"}"""
    }
}
