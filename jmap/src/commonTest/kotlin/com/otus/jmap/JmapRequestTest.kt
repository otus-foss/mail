package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmapRequestTest {
    private val wellKnown = "https://mail.example.org/.well-known/jmap"

    private suspend fun respondWith(body: String): JmapResponse {
        val (engine, _) = server { body }
        val client = JmapClient(engine)
        return client.send(client.session(wellKnown, ADA), ADA) {}
    }

    @Test
    fun typedCallsChainByResultReference() =
        runTest {
            val (engine, requests) =
                server {
                    """{"methodResponses": [["Mailbox/query", {"ids": ["a"], "queryState": "q"}, "c0"],
                       ["Mailbox/get", {"state": "m", "list": [{"id": "a", "role": "inbox"}], "notFound": []}, "c1"]],
                       "sessionState": "s1"}"""
                }
            val client = JmapClient(engine)
            val session = client.session(wellKnown, ADA)
            lateinit var query: JmapCall<QueryResult>
            lateinit var get: JmapCall<GetResult<Mailbox>>
            val response =
                client.send(session, ADA) {
                    query = call(QueryMethod(MAILBOX, "d1", sort = listOf(JmapSort("name")), limit = 10))
                    get = call(GetMethod(MAILBOX, "d1", JmapIds.Result(query.resultOf("/ids")), listOf("id", "role")))
                }
            assertEquals(listOf("a"), response[query].ids)
            assertEquals(Mailbox("a", "inbox"), response[get].list.single())
            val body = requests.last().body
            assertTrue("\"using\":[\"urn:ietf:params:jmap:core\",\"urn:ietf:params:jmap:mail\"]" in body, body)
            assertTrue("\"#ids\":{\"resultOf\":\"c0\",\"name\":\"Mailbox/query\",\"path\":\"/ids\"}" in body, body)
        }

    @Test
    fun malformedResponsesAreJmapErrorsNeverCrashes() =
        runTest {
            val malformed =
                listOf(
                    """{"methodResponses": [["Mailbox/get", {}]]}""",
                    """{"methodResponses": {"x": 1}}""",
                    """{"methodResponses": [[1, {}, "c0"]]}""",
                    """{"methodResponses": [["Mailbox/get", [], "c0"]]}""",
                    """{"sessionState": "s1"}""",
                    """[1, 2]""",
                    "not json",
                )
            malformed.forEach { body -> assertFailsWith<JmapException.NotJmap>(body) { respondWith(body) } }
        }

    @Test
    fun methodErrorsAndBadResultDataAreTyped() =
        runTest {
            val (engine, _) =
                server {
                    """{"methodResponses": [["error", {"type": "accountNotFound", "description": "gone"}, "c0"],
                       ["Mailbox/get", {"state": "m", "list": [{"role": "inbox"}]}, "c1"],
                       ["error", {"type": {"nested": 1}}, "c2"]]}"""
                }
            val client = JmapClient(engine)
            lateinit var first: JmapCall<GetResult<Mailbox>>
            lateinit var second: JmapCall<GetResult<Mailbox>>
            lateinit var third: JmapCall<GetResult<Mailbox>>
            val response =
                client.send(client.session(wellKnown, ADA), ADA) {
                    first = call(GetMethod(MAILBOX, "d1"))
                    second = call(GetMethod(MAILBOX, "d1"))
                    third = call(GetMethod(MAILBOX, "d1"))
                }
            assertEquals("accountNotFound", assertFailsWith<JmapException.MethodError> { response[first] }.type)
            assertFailsWith<JmapException.NotJmap> { response[second] }
            assertEquals("unknown", assertFailsWith<JmapException.MethodError> { response[third] }.type)
        }

    @Test
    fun missingCapabilitiesAreReportedBeforeSending() =
        runTest {
            val requests = mutableListOf<Recorded>()
            val engine =
                MockEngine { request ->
                    requests += request.record()
                    json(session(mail = false))
                }
            val client = JmapClient(engine)
            val session = client.session(wellKnown, ADA)
            val error =
                assertFailsWith<JmapException.Unsupported> {
                    client.request(
                        session,
                        ADA,
                    ) { call(GetMethod(MAILBOX, "d1")) }
                }
            assertEquals(JmapCapability.MAIL, error.capability)
            assertEquals(1, requests.size, "Nothing was sent for the unsupported call")
        }

    @Test
    fun oversizedResponsesAreRefused() =
        runTest {
            val policy = JmapPolicy(maxResponseBytes = 64)
            val declared =
                MockEngine { respond("x".repeat(65), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "65")) }
            assertFailsWith<JmapException.ResponseTooLarge> { JmapClient(declared, policy).session(wellKnown, ADA) }
            val streamed = MockEngine { respond("x".repeat(65), HttpStatusCode.OK) }
            assertFailsWith<JmapException.ResponseTooLarge> { JmapClient(streamed, policy).session(wellKnown, ADA) }
        }

    @Test
    fun customMethodsUseTheSameMachinery() =
        runTest {
            val echo =
                object : JmapMethod<String>("Core/echo", JmapCapability.CORE, null) {
                    override fun arguments() = JsonObject(mapOf("hello" to JsonPrimitive("world")))

                    override fun decode(arguments: JsonObject) = (arguments["hello"] as JsonPrimitive).content
                }
            val (engine, _) = server { """{"methodResponses": [["Core/echo", {"hello": "world"}, "c0"]]}""" }
            val client = JmapClient(engine)
            lateinit var echoCall: JmapCall<String>
            val response = client.send(client.session(wellKnown, ADA), ADA) { echoCall = call(echo) }
            assertEquals("world", response[echoCall])
        }
}
