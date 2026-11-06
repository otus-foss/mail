package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.inbox.domain.MailScope
import com.otus.mail.feature.inbox.domain.MailboxScope
import com.otus.mail.testing.stalwartLike
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JmapMailboxScopeTest {
    private val ada = JmapLogin("ada", "https://mail.example.org/jmap/session", "ada@example.org", "Ada")

    @Test
    fun accountChoicesDoNotConnectToExcludedLogins() =
        runTest {
            val server = FakeMailServer(MAILBOXES)
            val store =
                InMemoryLoginStore().apply {
                    save(ada, "secret")
                    save(ada.copy(id = "empty", name = "Empty", username = "empty@example.org"), "wrong")
                }
            val source = source(stalwartLike(api = server::answer), store)
            val chosen = MailScope.Mailboxes(setOf(MailboxScope.Inbox), setOf("ada"))
            assertTrue(source.messages(chosen).unavailable.isEmpty())
            assertEquals(1, server.calls("Email/query").size)
            assertTrue(server.bodies.any { "\"inMailbox\":\"a\"" in it })
            assertTrue(source.messages(MailScope.Mailboxes(emptySet(), setOf("ada"))).unavailable.isEmpty())
            assertEquals(4, server.calls("Email/query").size, "All mailboxes still stay in the selected login")
        }

    @Test
    fun accountSpecificMailboxChoicesKeepTheirLoginIdentity() =
        runTest {
            val store =
                InMemoryLoginStore().apply {
                    save(ada, "secret")
                    save(ada.copy(id = "other", username = "other@example.org"), "wrong")
                }
            val engine = stalwartLike(api = FakeMailServer(MAILBOXES)::answer)
            val scope = MailScope.Mailboxes(setOf(MailboxScope("ada", "Receipts")))
            assertTrue(source(engine, store).messages(scope).unavailable.isEmpty())
        }

    @Test
    fun loadingEveryMailboxKeepsOneRequestInFlightPerLogin() =
        runTest {
            val mutex = Mutex()
            var active = 0
            var peak = 0
            val server = FakeMailServer(MAILBOXES)
            val engine =
                MockEngine { request ->
                    val text =
                        when {
                            request.method == HttpMethod.Get -> {
                                SESSION
                            }

                            else -> {
                                mutex.withLock {
                                    active += 1
                                    peak = maxOf(peak, active)
                                }
                                delay(20)
                                mutex.withLock { active -= 1 }
                                server.answer((request.body as TextContent).text)
                            }
                        }
                    respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val store = InMemoryLoginStore().apply { save(ada, "secret") }
            assertTrue(source(engine, store).messages(MailScope.AllMailboxes).unavailable.isEmpty())
            assertEquals(1, peak)
        }

    private fun source(
        engine: MockEngine,
        store: LoginStore,
    ) = JmapMailSource(JmapConnections(JmapClient(engine), store))

    private companion object {
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {"maxConcurrentRequests": 1},
            "urn:ietf:params:jmap:mail": {}}, "accounts": {"mail": {"name": "Ada",
            "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "mail"},
            "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s"}"""
        const val MAILBOXES = """{"id": "a", "name": "Posteingang", "role": "inbox"},
            {"id": "r", "name": "Receipts"}, {"id": "s", "name": "Sent", "role": "sent"}"""
    }
}
