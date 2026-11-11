package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.CacheWrite
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.RecoveringMailCache
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.data.receivedAt
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.feature.inbox.domain.InboxStorage
import com.otusfoss.mail.feature.inbox.domain.InboxStorageException
import com.otusfoss.mail.feature.read.data.JmapMessageBodies
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.source
import com.otusfoss.mail.testing.stalwartLike
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
