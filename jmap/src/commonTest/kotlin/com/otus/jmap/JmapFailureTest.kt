package com.otus.jmap

import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JmapFailureTest {
    @Test
    fun networkFailuresAreTypedButCancellationIsPreserved() =
        runTest {
            val failure = IOException("connection failed")
            val broken = JmapClient(MockEngine { throw failure })
            val error = assertFailsWith<JmapException.Unreachable> { broken.session("https://example.org/s", ADA) }
            assertEquals(failure.message, assertIs<IOException>(error.cause).message)
            val cancelled = JmapClient(MockEngine { throw CancellationException("cancelled") })
            assertFailsWith<CancellationException> { cancelled.session("https://example.org/s", ADA) }
        }

    @Test
    fun capabilityMustBelongToTheSelectedAccount() =
        runTest {
            val (engine, requests) = server()
            val client = JmapClient(engine)
            val session = client.session("https://mail.example.org/jmap/session", ADA)
            val unsupported =
                session.copy(
                    accounts =
                        session.accounts.mapValues { (_, account) ->
                            account.copy(capabilities = emptySet())
                        },
                )
            assertFailsWith<JmapException.Unsupported> {
                client.request(unsupported, ADA) { call(GetMethod(MAILBOX, "d1")) }
            }
            assertEquals(1, requests.size)
        }

    @Test
    fun changesPreserveContinuationStateAndObjectIds() =
        runTest {
            val (engine, requests) =
                server {
                    """{"methodResponses":[["Mailbox/changes",{"oldState":"before","newState":"after",
                "hasMoreChanges":true,"created":["a"],"updated":["b"],"destroyed":["c"]},"c0"]]}"""
                }
            val connection = JmapConnection(JmapClient(engine), "https://mail.example.org/jmap/session", ADA)
            val result = connection.call(ChangesMethod(MAILBOX, "d1", "before", 10))
            assertEquals(ChangesResult("before", "after", true, listOf("a"), listOf("b"), listOf("c")), result)
            val body = JmapJson.parseToJsonElement(requests.last().body).toString()
            kotlin.test.assertTrue("\"sinceState\":\"before\"" in body)
            kotlin.test.assertTrue("\"maxChanges\":10" in body)
        }
}
