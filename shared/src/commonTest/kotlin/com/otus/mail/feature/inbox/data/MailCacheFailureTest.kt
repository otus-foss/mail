package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.cache.CacheStorageException
import com.otus.mail.core.cache.CacheWrite
import com.otus.mail.core.cache.InMemoryMailCache
import com.otus.mail.core.cache.MailCache
import com.otus.mail.core.cache.RecoveringMailCache
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.InboxStorage
import com.otus.mail.feature.inbox.domain.InboxStorageException
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MailCacheFailureTest {
    private val login = ADA_LOGIN
    private val store = InMemoryLoginStore().apply { save(login, "secret") }

    private fun failingWrites(): MailCache =
        RecoveringMailCache(
            object : MailCache by InMemoryMailCache() {
                override suspend fun write(
                    loginId: String,
                    changes: CacheWrite.() -> Unit,
                ): Unit = throw CacheStorageException(IllegalStateException("disk full"))
            },
        )

    @Test
    fun aFetchedBodyOpensWhenItsCacheWriteFails() =
        runTest {
            val engine =
                stalwartLike {
                    """{"methodResponses": [["Email/get", {"state": "e", "list": [{
                "id": "e", "receivedAt": "2026-10-24T12:00:00Z", "attachments": [],
                "textBody": [{"partId": "1", "type": "text/plain"}],
                "bodyValues": {"1": {"value": "Keep this body"}}
            }]}, "c0"]]}"""
                }
            val bodies = JmapMessageBodies(JmapConnections(JmapClient(engine), store), failingWrites())
            val message =
                MailMessage(
                    "ada/e",
                    MailSender("Sender", "sender@example.org"),
                    "Subject",
                    "preview",
                    ReceivedAt("2026-10-24", "12:00"),
                    MailLocation("Inbox", "Ada", "ada", mailboxRole = MailboxRole.Inbox),
                    emailId = "e",
                )
            assertEquals("Keep this body", bodies.content(message)?.text)
        }

    @Test
    fun anInboxWriteFailureReportsStorageAndRetryUsesMemory() =
        runTest {
            val server =
                FakeMailServer(
                    """{"id":"in","name":"Inbox","role":"inbox"}""",
                    """{"id":"e","threadId":"t","receivedAt":"2026-10-24T12:00:00Z","mailboxIds":{"in":true}}""",
                )
            val source = server.source(store, failingWrites())
            val failure = assertFailsWith<InboxStorageException> { source.messages(MailScope.Inbox) }
            assertEquals(InboxStorage.Cache, failure.storage)
            assertEquals(listOf("e"), source.messages(MailScope.Inbox).messages.map { it.emailId })
        }
}
