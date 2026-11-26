package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.core.jmap.login.JmapLogin
import com.otusfoss.mail.core.cache.InMemoryMailCache
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MessageFlag
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.FakeMailServer
import com.otusfoss.mail.testing.STALWART_SESSION
import com.otusfoss.mail.testing.connections
import com.otusfoss.mail.testing.emailWriter
import com.otusfoss.mail.testing.source
import com.otusfoss.mail.testing.stalwartLike
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
import kotlin.time.Instant

/** How [EmailChangeQueue] handles failing batches, read-only accounts, and rejected sign-ins. */
class EmailChangeQueueFailureTest {
    private val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    private val cache = InMemoryMailCache()

    @Test
    fun aChangeTheServerKeepsFailingOnDoesNotHoldBackOtherEmailsAndLeavesAfterThreeTries() =
        runTest {
            val server = FakeMailServer(INBOX, "${email("m1")}, ${email("m2")}")
            val connections =
                JmapConnections(
                    JmapClient(
                        stalwartLike(STALWART_SESSION) { body ->
                            if ("Email/set" in body && "\"m1\"" in body) SERVER_FAIL else server.answer(body)
                        },
                    ),
                    store,
                )
            val queue = EmailChangeQueue(connections, cache, store = null)
            val read = JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true)))

            queue.update(ADA_LOGIN.id, mapOf("m1" to read))
            assertEquals(setOf("m2"), queue.update(ADA_LOGIN.id, mapOf("m2" to read)))
            assertTrue(server.seen("m2"), "The second email reaches the server despite the failing one")

            // Third try drops m1 so later background runs and deletes are no longer blocked.
            assertFalse(queue.flushAll(), "The third unclear failure drops m1 and leaves nothing to retry")
            assertEquals("ran", queue.afterSending(ADA_LOGIN.id) { "ran" })
        }

    @Test
    fun aServerFailingEveryEmailStopsAfterTwoUnclearFailures() =
        runTest {
            val server = FakeMailServer(INBOX, "${email("m1")}, ${email("m2")}, ${email("m3")}")
            var setCalls = 0
            val connections =
                JmapConnections(
                    JmapClient(
                        stalwartLike(STALWART_SESSION) { body ->
                            if ("Email/set" in body) {
                                setCalls += 1
                                SERVER_FAIL
                            } else {
                                server.answer(body)
                            }
                        },
                    ),
                    store,
                )
            val queue = EmailChangeQueue(connections, cache, store = null)
            val read = JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true)))

            queue.update(ADA_LOGIN.id, mapOf("m1" to read, "m2" to read, "m3" to read))

            // One failing batch call plus two single-email tries before stopping.
            assertEquals(4, setCalls, "Two changes, then one alone, then an empty Email/set to see if the server works")
            assertTrue(queue.flushAll(), "Changes still wait for a retry after stopping early")
        }

    @Test
    fun aBatchRefusedAsTooLargeFallsBackToSingleChanges() =
        runTest {
            val server = FakeMailServer(INBOX, "${email("m1")}, ${email("m2")}")
            val connections =
                JmapConnections(
                    JmapClient(
                        stalwartLike(STALWART_SESSION) { body ->
                            if ("Email/set" in body && "\"m1\"" in body && "\"m2\"" in body) {
                                REQUEST_TOO_LARGE
                            } else {
                                server.answer(body)
                            }
                        },
                    ),
                    store,
                )
            val queue = EmailChangeQueue(connections, cache, store = null)
            val read = JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true)))

            assertEquals(setOf("m1", "m2"), queue.update(ADA_LOGIN.id, mapOf("m1" to read, "m2" to read)))
            assertTrue(server.seen("m1"))
            assertTrue(server.seen("m2"))
            assertFalse(queue.flushAll())
        }

    @Test
    fun aCallRefusedBecauseTheAccountIsReadOnlyKeepsTheChangesMadeOffline() =
        runTest {
            val server = FakeMailServer(INBOX, email("m1"))
            val queue = EmailChangeQueue(server.connections(store), cache, store = null)
            val source = server.source(store, cache, queue = queue)
            val writer = emailWriter(server.connections(store), cache, queue)

            val message = source.messages(MailScope.Inbox).messages.single()
            server.down = true
            assertEquals(setOf(message.id), writer.mark(listOf(message), MessageFlag.Read, on = true))
            server.down = false
            server.refused = setOf("Email/set")
            server.refusalType = "accountReadOnly"

            assertFalse(queue.flushAll(), "A read-only account waits without retrying in the background")

            server.refused = emptySet()
            assertFalse(queue.flushAll())
            assertTrue(server.seen("m1"), "The offline mark reaches the server once the account takes writes")
        }

    @Test
    fun aRejectedSignInDoesNotAskTheBackgroundJobToRunAgain() =
        runTest {
            val server = FakeMailServer(INBOX, email("m1"))
            val wakes = mutableListOf<Instant>()
            val queue = EmailChangeQueue(server.connections(store), cache, store = null, wake = { wakes += it })
            store.save(GRACE_LOGIN, "secret")
            val read = mapOf("m1" to JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true))))

            assertEquals(setOf("m1"), queue.update(GRACE_LOGIN.id, read))
            assertTrue(wakes.isEmpty(), "The queue asks for no background run on rejected sign-in")
            assertFalse(queue.flushAll(), "A rejected sign-in does not ask the background job to retry")
        }

    @Test
    fun anOfflineAccountStillAsksTheBackgroundJobToRetry() =
        runTest {
            val server = FakeMailServer(INBOX, email("m1"))
            val wakes = mutableListOf<Instant>()
            val queue = EmailChangeQueue(server.connections(store), cache, store = null, wake = { wakes += it })
            val read = mapOf("m1" to JsonObject(mapOf("keywords/\$seen" to JsonPrimitive(true))))
            server.down = true

            assertEquals(setOf("m1"), queue.update(ADA_LOGIN.id, read))
            assertEquals(1, wakes.size)
            assertTrue(queue.flushAll())
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

    private companion object {
        val GRACE_LOGIN = JmapLogin("grace", ADA_LOGIN.sessionUrl, "grace@example.org", "Grace")

        const val INBOX = """{"id": "a", "name": "Inbox", "role": "inbox"}"""
        const val SERVER_FAIL = """{"methodResponses": [["error", {"type": "serverFail"}, "c0"]]}"""
        const val REQUEST_TOO_LARGE = """{"methodResponses": [["error", {"type": "requestTooLarge"}, "c0"]]}"""

        fun email(id: String) =
            """{"id": "$id", "threadId": "t-$id", "subject": "Hi", "mailboxIds": {"a": true}, "keywords": {},
            "receivedAt": "2026-10-18T10:00:00Z"}"""
    }
}
