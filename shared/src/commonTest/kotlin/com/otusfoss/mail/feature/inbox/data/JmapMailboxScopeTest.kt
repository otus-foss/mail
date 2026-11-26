package com.otusfoss.mail.feature.inbox.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.core.jmap.login.LoginStore
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailboxScope
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.MailQuery
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.mailSource
import com.otusfoss.mail.testing.stalwartLike
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

    @Test
    fun allMailLeavesOutSpamTrashAndTheirSubfoldersUntilIncluded() =
        runTest {
            val server = FakeMailServer(MAILBOXES_WITH_BINS)
            val store = InMemoryLoginStore().apply { save(ada, "secret") }
            val source = source(stalwartLike(api = server::answer), store)
            assertTrue(source.messages(MailScope.AllMail).unavailable.isEmpty())
            val defaultQueried = server.bodies.filter { "Email/query" in it }
            assertTrue(defaultQueried.any { "\"inMailbox\":\"a\"" in it })
            assertTrue(defaultQueried.any { "\"inMailbox\":\"r\"" in it })
            assertTrue(defaultQueried.any { "\"inMailbox\":\"s\"" in it })
            assertTrue(defaultQueried.none { "\"inMailbox\":\"j\"" in it })
            assertTrue(defaultQueried.none { "\"inMailbox\":\"t\"" in it })
            assertTrue(defaultQueried.none { "\"inMailbox\":\"ts\"" in it })

            val withSpam = MailQuery(InboxFilters(includedBins = setOf(MailboxRole.Junk)))
            assertTrue(source.messages(MailScope.AllMail, withSpam).unavailable.isEmpty())
            val afterSpam = server.bodies.filter { "Email/query" in it }
            assertTrue(afterSpam.any { "\"inMailbox\":\"j\"" in it })
            assertTrue(afterSpam.none { "\"inMailbox\":\"t\"" in it })

            val onlyTrash =
                MailQuery(
                    InboxFilters(folders = setOf(FolderRef("ada", "t", includeSubfolders = false))),
                    folderIds = mapOf("ada" to setOf("t")),
                )
            val beforeTrash = server.calls("Email/query").size
            assertTrue(source.messages(MailScope.AllMail, onlyTrash).unavailable.isEmpty())
            assertEquals(beforeTrash + 1, server.calls("Email/query").size)
            assertTrue(server.bodies.last().contains("\"inMailbox\":\"t\""))
        }

    private fun source(
        engine: MockEngine,
        store: LoginStore,
    ) = mailSource(JmapConnections(JmapClient(engine), store))

    private companion object {
        const val SESSION = """{"capabilities": {"urn:ietf:params:jmap:core": {"maxConcurrentRequests": 1},
            "urn:ietf:params:jmap:mail": {}}, "accounts": {"mail": {"name": "Ada",
            "accountCapabilities": {"urn:ietf:params:jmap:mail": {}}}},
            "primaryAccounts": {"urn:ietf:params:jmap:mail": "mail"},
            "username": "ada@example.org", "apiUrl": "/jmap/", "state": "s"}"""
        const val MAILBOXES = """{"id": "a", "name": "Posteingang", "role": "inbox"},
            {"id": "r", "name": "Receipts"}, {"id": "s", "name": "Sent", "role": "sent"}"""
        const val MAILBOXES_WITH_BINS = """{"id": "a", "name": "Posteingang", "role": "inbox"},
            {"id": "r", "name": "Receipts"}, {"id": "s", "name": "Sent", "role": "sent"},
            {"id": "j", "name": "Spam", "role": "junk"}, {"id": "t", "name": "Trash", "role": "trash"},
            {"id": "ts", "name": "Old trash", "parentId": "t"}"""
    }
}
