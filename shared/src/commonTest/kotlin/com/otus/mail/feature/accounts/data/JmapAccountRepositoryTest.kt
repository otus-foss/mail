package com.otus.mail.feature.accounts.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginSecret
import com.otus.mail.feature.accounts.domain.ConnectFailure
import com.otus.mail.feature.accounts.domain.ConnectOutcome
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.stalwartLike
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JmapAccountRepositoryTest {
    private val draft = JmapAccountDraft("https://mail.example.org/.well-known/jmap", "ada@example.org", "secret")
    private val mailboxes = """{"methodResponses": [["Mailbox/get", {"state": "m", "list": [{"id": "a"}]}, "c0"]]}"""
    private val store = InMemoryLoginStore()

    private fun repository(engine: MockEngine): JmapAccountRepository {
        val client = JmapClient(engine)
        return JmapAccountRepository(client, JmapConnections(client, store), store)
    }

    @Test
    fun connectingVerifiesMailAndSavesTheLoginWithItsSecret() =
        runTest {
            val connected = assertIs<ConnectOutcome.Connected>(repository(stalwartLike { mailboxes }).connect(draft))
            assertEquals("ada@example.org", connected.account.name)
            assertEquals("mail.example.org", connected.account.server)
            val saved = store.logins().single()
            assertEquals(draft.sessionUrl, saved.sessionUrl)
            assertIs<LoginSecret.Found>(store.secret(saved.id))
        }

    @Test
    fun reconnectingReplacesTheExistingLogin() =
        runTest {
            store.save(JmapLogin("earlier-id", draft.sessionUrl, draft.username, "Ada"), "old")
            repository(stalwartLike { mailboxes }).connect(draft)
            assertEquals(listOf("earlier-id"), store.logins().map { it.id })
        }

    @Test
    fun failuresSaveNothing() =
        runTest {
            val wrong = repository(stalwartLike()).connect(draft.copy(password = "other"))
            assertEquals(ConnectOutcome.Failed(ConnectFailure.WrongCredentials), wrong)
            val noMail = STALWART_SESSION.replace("urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:calendars")
            assertEquals(ConnectOutcome.Failed(ConnectFailure.NoMail), repository(stalwartLike(noMail)).connect(draft))
            val colon = repository(stalwartLike()).connect(draft.copy(username = "a:da"))
            assertEquals(ConnectOutcome.Failed(ConnectFailure.InvalidUsername), colon)
            assertTrue(store.logins().isEmpty())
        }

    @Test
    fun anotherHostNeedsConsentAndIsRememberedOnceTrusted() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.host) {
                        "mail.example.org" -> {
                            respond(
                                "",
                                HttpStatusCode.TemporaryRedirect,
                                headersOf(HttpHeaders.Location, "https://login.example.net/s"),
                            )
                        }

                        else -> {
                            if (request.url.encodedPath == "/s") {
                                respond(
                                    STALWART_SESSION,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            } else {
                                respond(
                                    mailboxes,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                    }
                }
            val confirm = assertIs<ConnectOutcome.ConfirmHost>(repository(engine).connect(draft))
            assertEquals("login.example.net", confirm.host)
            assertTrue(store.logins().isEmpty())
            assertIs<ConnectOutcome.Connected>(
                repository(engine).connect(draft.copy(trustedOrigins = setOf(confirm.origin))),
            )
            assertEquals(setOf(confirm.origin), store.logins().single().trustedOrigins)
        }
}
