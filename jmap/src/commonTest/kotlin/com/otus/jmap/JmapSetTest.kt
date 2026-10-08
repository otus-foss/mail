package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapSetTest {
    private val wellKnown = "https://mail.example.org/.well-known/jmap"

    private suspend fun set(
        method: SetMethod,
        response: String,
    ): Pair<SetResult, String> {
        val (engine, requests) = server { response }
        val client = JmapClient(engine)
        val result = client.request(client.session(wellKnown, ADA), ADA) { call(method) }
        return result to requests.last().body
    }

    @Test
    fun createsUpdatesAndDestroysInOneCallWithCreationReferences() =
        runTest {
            val method =
                SetMethod(
                    MAILBOX,
                    "d1",
                    create = mapOf("new" to buildJsonObject { put("name", JsonPrimitive("Receipts")) }),
                    update = mapOf("a" to buildJsonObject { put("parentId", JsonPrimitive("#new")) }),
                    destroy = listOf("b"),
                    ifInState = "m1",
                    extra = mapOf("onDestroyRemoveEmails" to JsonPrimitive(false)),
                )
            val (result, body) =
                set(
                    method,
                    """{"methodResponses": [["Mailbox/set", {"oldState": "m1", "newState": "m2",
                       "created": {"new": {"id": "c9", "totalEmails": 0}}, "updated": {"a": null},
                       "destroyed": ["b"], "notCreated": null}, "c0"]]}""",
                )
            assertEquals("c9", result.createdId("new"))
            assertEquals("m2", result.newState)
            assertEquals(listOf("b"), result.destroyed)
            assertTrue("a" in result.updated)
            assertNull(result.firstError)
            listOf(
                "\"ifInState\":\"m1\"",
                "\"create\":{\"new\":{\"name\":\"Receipts\"}}",
                "\"update\":{\"a\":{\"parentId\":\"#new\"}}",
                "\"destroy\":[\"b\"]",
                "\"onDestroyRemoveEmails\":false",
            ).forEach { assertTrue(it in body, body) }
        }

    @Test
    fun rejectedObjectsKeepTheirReasons() =
        runTest {
            val (result, _) =
                set(
                    SetMethod(MAILBOX, "d1", create = mapOf("new" to JsonObject(emptyMap())), destroy = listOf("b")),
                    """{"methodResponses": [["Mailbox/set", {"newState": "m2",
                       "notCreated": {"new": {"type": "alreadyExists", "existingId": "x1", "description": "taken"}},
                       "notDestroyed": {"b": {"type": "mailboxHasEmail"}}}, "c0"]]}""",
                )
            assertNull(result.createdId("new"))
            assertEquals(SetError("alreadyExists", "taken", existingId = "x1"), result.firstError)
            assertEquals("mailboxHasEmail", result.notDestroyed.getValue("b").type)
        }

    @Test
    fun aStateMismatchFailsTheWholeCall() =
        runTest {
            val error =
                assertFailsWith<JmapException.MethodError> {
                    set(
                        SetMethod(MAILBOX, "d1", destroy = listOf("b"), ifInState = "old"),
                        """{"methodResponses": [["error", {"type": "stateMismatch"}, "c0"]]}""",
                    )
                }
            assertEquals("stateMismatch", error.type)
        }

    @Test
    fun malformedSetResultsAreJmapErrors() =
        runTest {
            listOf(
                """{"methodResponses": [["Mailbox/set", {"notCreated": {"new": {"description": "no type"}}}, "c0"]]}""",
                """{"methodResponses": [["Mailbox/set", {"created": ["not", "a", "map"]}, "c0"]]}""",
            ).forEach { body ->
                assertFailsWith<JmapException.NotJmap>(body) { set(SetMethod(MAILBOX, "d1"), body) }
            }
        }

    @Test
    fun accountsWithoutTheCapabilityAreNotSent() =
        runTest {
            val client = JmapClient(MockEngine { json(session(mail = false)) })
            assertFailsWith<JmapException.Unsupported> {
                client.request(client.session(wellKnown, ADA), ADA) { call(SetMethod(MAILBOX, "d1")) }
            }
        }
}
