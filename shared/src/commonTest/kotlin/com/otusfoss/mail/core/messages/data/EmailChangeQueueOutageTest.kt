package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.stalwartLike
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Changes wait while the whole server fails or is busy, instead of leaving the queue unseen. */
class EmailChangeQueueOutageTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()
    private val read = JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true)))

    @Test
    fun aServerThatFailsEveryCallKeepsTheChangesWaiting() = runTest { assertOutageKeepsChanges(SERVER_FAIL) }

    @Test
    fun aCaptivePortalPageKeepsTheChangesWaiting() =
        runTest { assertOutageKeepsChanges("<html>Sign in to the Wi-Fi</html>") }

    @Test
    fun aConcurrencyLimitAnswerKeepsTheChangeWaiting() =
        runTest {
            val server = FakeMailServer(INBOX, email("m1"))
            var limited = true
            val engine =
                statusServer { body ->
                    if (limited &&
                        "Email/set" in body
                    ) {
                        HttpStatusCode.BadRequest to LIMIT
                    } else {
                        HttpStatusCode.OK to
                            server.answer(body)
                    }
                }
            val queue = EmailChangeQueue(JmapConnections(JmapClient(engine), store), cache, store = null)

            val shown = queue.update(ADA_LOGIN.id, mapOf("m1" to read))
            limited = false
            queue.flushAll()

            assertEquals(setOf("m1"), shown, "The mark counts as made")
            assertTrue(server.seen("m1"), "The mark goes once the server has room")
        }

    /** Every `Email/set` gets [outage] for three sends; then the server works again. */
    private suspend fun assertOutageKeepsChanges(outage: String) {
        val server = FakeMailServer(INBOX, "${email("m1")}, ${email("m2")}, ${email("m3")}")
        var answer: String? = outage
        val connections =
            JmapConnections(
                JmapClient(
                    stalwartLike(STALWART_SESSION) { body ->
                        answer?.takeIf { "Email/set" in body } ?: server.answer(body)
                    },
                ),
                store,
            )
        val queue = EmailChangeQueue(connections, cache, store = null)

        val shown = queue.update(ADA_LOGIN.id, mapOf("m1" to read, "m2" to read, "m3" to read))
        queue.flushAll()
        queue.flushAll()
        answer = null
        queue.flushAll()

        assertEquals(setOf("m1", "m2", "m3"), shown, "All three marks counted as made")
        assertTrue(server.seen("m3"), "The third mark arrives once the server works")
        assertEquals("ran", queue.afterSending(ADA_LOGIN.id) { "ran" }, "Nothing waits any more")
        assertTrue(server.seen("m1"), "The first mark waited for the server")
        assertTrue(server.seen("m2"), "The second mark waited too")
    }

    private fun FakeMailServer.seen(id: String): Boolean {
        val body = """{"methodCalls": [["Email/get", {"ids": ["$id"]}, "c0"]]}"""
        val email =
            Json
                .parseToJsonElement(answer(body))
                .jsonObject
                .getValue("methodResponses")
                .jsonArray[0]
                .jsonArray[1]
                .jsonObject
                .getValue("list")
                .jsonArray
                .single()
                .jsonObject
        return (email.getValue("keywords") as JsonObject)["\$seen"] == JsonPrimitive(true)
    }

    private fun statusServer(api: (String) -> Pair<HttpStatusCode, String>): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                addHandler { request ->
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when (request.url.encodedPath) {
                        "/.well-known/jmap" -> {
                            respond(
                                "",
                                HttpStatusCode.TemporaryRedirect,
                                headersOf(HttpHeaders.Location, "/jmap/session"),
                            )
                        }

                        "/jmap/session" -> {
                            respond(STALWART_SESSION, HttpStatusCode.OK, json)
                        }

                        else -> {
                            val (status, body) = api((request.body as TextContent).text)
                            respond(body, status, json)
                        }
                    }
                }
            },
        )

    private companion object {
        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}"""
        const val SERVER_FAIL = """{"methodResponses": [["error", {"type": "serverFail"}, "c0"]]}"""
        const val LIMIT =
            """{"type": "urn:ietf:params:jmap:error:limit", "limit": "maxConcurrentRequests", "status": 400}"""

        fun email(id: String) =
            """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T10:00:00Z"}"""
    }
}
