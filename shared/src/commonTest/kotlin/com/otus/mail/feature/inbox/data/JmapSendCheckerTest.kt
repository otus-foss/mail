package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.SendCheck
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Finding a sent draft by its Message-ID after a send whose answer was lost. */
class JmapSendCheckerTest {
    private val draft = Draft(ADA_LOGIN.id, emailId = "d1", messageId = "x@otus.mail")
    private val answered = AnsweredMessage("o1", forwarded = false)
    private val sets = mutableListOf<JsonObject>()

    private fun checker(
        found: String,
        queryError: String? = null,
    ): JmapSendChecker {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike { body ->
                val responses =
                    Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                        val (name, arguments, id) = call.jsonArray
                        val method = name.jsonPrimitive.content
                        val result =
                            when {
                                queryError != null && method == "Email/query" -> {
                                    """["error", {"type": "$queryError"}"""
                                }

                                method == "Email/query" -> {
                                    """["$method", {"queryState": "q", "ids": [], "position": 0}"""
                                }

                                method == "Email/get" -> {
                                    """["$method", {"state": "e", "list": [$found], "notFound": []}"""
                                }

                                else -> {
                                    setAnswer(method, arguments.jsonObject)
                                }
                            }
                        "$result, $id]"
                    }
                """{"methodResponses": [${responses.joinToString()}]}"""
            }
        return JmapSendChecker(JmapConnections(JmapClient(engine), store), store)
    }

    /** Records an `Email/set` and confirms every update and destroy in it. */
    private fun setAnswer(
        method: String,
        arguments: JsonObject,
    ): String {
        sets += arguments
        val updated =
            arguments["update"]
                ?.jsonObject
                ?.keys
                .orEmpty()
                .joinToString { """"$it": null""" }
        val destroyed = arguments["destroy"]?.toString() ?: "[]"
        return """["$method", {"newState": "n", "updated": {$updated}, "destroyed": $destroyed}"""
    }

    @Test
    fun aCopyThatWentIsTidiedUpAsAfterASend() =
        runTest {
            val check =
                checker(
                    found = """{"id": "e9", $RECEIVED, "keywords": {"${'$'}seen": true}}""",
                ).check(draft, answered, false)
            assertEquals(SendCheck.Sent, check)
            val tidy = sets.single()
            assertEquals(listOf("d1"), (tidy.getValue("destroy") as JsonArray).map { it.jsonPrimitive.content })
            assertTrue("o1" in tidy.getValue("update").jsonObject, "The answered message gets its mark")
        }

    @Test
    fun aCopyThatKeptItsDraftMarkIsDestroyedAndCountsAsNotSent() =
        runTest {
            val check =
                checker(
                    found = """{"id": "e9", $RECEIVED, "keywords": {"${'$'}draft": true}}""",
                ).check(draft, answered, false)
            assertEquals(SendCheck.NotSent, check)
            assertEquals(
                listOf("e9"),
                (sets.single().getValue("destroy") as JsonArray).map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun aServerDownForNowIsAskedAgainAndOneWithoutTheFilterLeavesItToTheUser() =
        runTest {
            assertEquals(SendCheck.Unreachable, checker("", queryError = "serverUnavailable").check(draft, null, false))
            assertEquals(
                SendCheck.Unconfirmable,
                checker("", queryError = "unsupportedFilter").check(draft, null, false),
            )
        }

    private companion object {
        const val RECEIVED = """"receivedAt": "2026-10-31T12:00:00Z""""
    }
}
