package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.SendCheck
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.SUBMISSION_SESSION
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

    private val methods = mutableListOf<String>()

    /**
     * A server whose `Email/get` finds [found] and whose only submission, when [submitted] is set,
     * is `s1` with that `undoStatus`.
     */
    private fun checker(
        found: String,
        queryError: String? = null,
        submitted: String? = null,
    ): JmapSendChecker {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike(SUBMISSION_SESSION) { body ->
                val responses =
                    Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                        val (name, arguments, id) = call.jsonArray
                        val method = name.jsonPrimitive.content
                        methods += method
                        val submissions = submitted?.let { """{"id": "s1", "undoStatus": "$it"}""" }.orEmpty()
                        val submissionIds = if (submitted == null) "" else "\"s1\""
                        val result =
                            when {
                                method == "EmailSubmission/query" -> {
                                    """["$method", {"queryState": "q", "ids": [$submissionIds], "position": 0}"""
                                }

                                method == "EmailSubmission/get" -> {
                                    """["$method", {"state": "s", "list": [$submissions], "notFound": []}"""
                                }

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
        return JmapSendChecker(JmapConnections(JmapClient(engine), store))
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

    @Test
    fun aSubmissionOfTheStoredCopyMeansItWentWithoutAskingASearchIndex() =
        runTest {
            val copy = """{"id": "e9", $RECEIVED, "keywords": {"${'$'}draft": true}}"""
            val check = checker(found = copy, submitted = "pending").check(draft, answered, false, storedCopy = "e9")
            assertEquals(SendCheck.Sent, check)
            assertTrue("Email/query" !in methods, "A header search may not know the copy yet")
            assertEquals(
                listOf("d1"),
                sets
                    .single()
                    .getValue("destroy")
                    .jsonArray
                    .map { it.jsonPrimitive.content },
            )
        }

    @Test
    fun aStoredCopyThatKeptItsDraftMarkWithoutASubmissionIsDestroyedAndGoesAgain() =
        runTest {
            val copy = """{"id": "e9", $RECEIVED, "keywords": {"${'$'}draft": true}}"""
            assertEquals(SendCheck.NotSent, checker(found = copy).check(draft, answered, false, storedCopy = "e9"))
            assertEquals(
                listOf("e9"),
                sets
                    .single()
                    .getValue("destroy")
                    .jsonArray
                    .map { it.jsonPrimitive.content },
            )

            sets.clear()
            val canceled = checker(found = copy, submitted = "canceled")
            assertEquals(SendCheck.NotSent, canceled.check(draft, answered, false, storedCopy = "e9"))
        }

    @Test
    fun aStoredCopyWithoutItsDraftMarkWentThoughTheServerDroppedItsSubmission() =
        runTest {
            val copy = """{"id": "e9", $RECEIVED, "keywords": {"${'$'}seen": true}}"""
            assertEquals(SendCheck.Sent, checker(found = copy).check(draft, answered, false, storedCopy = "e9"))
        }

    @Test
    fun withNeitherCopyNorSubmissionOnlyTheUserCanTellUnlessTheSendWasWithdrawn() =
        runTest {
            assertEquals(SendCheck.Unconfirmable, checker(found = "").check(draft, answered, false, storedCopy = "e9"))
            assertEquals(SendCheck.NotSent, checker(found = "").check(draft, answered, true, storedCopy = "e9"))
            assertTrue(sets.isEmpty(), "Nothing is destroyed or marked")
        }

    @Test
    fun aWithdrawnSendCancelsTheHoldOfItsStoredCopyAndDestroysIt() =
        runTest {
            val copy = """{"id": "e9", $RECEIVED, "keywords": {}}"""
            val check = checker(found = copy, submitted = "pending").check(draft, answered, true, storedCopy = "e9")
            assertEquals(SendCheck.NotSent, check)
            val (cancel, destroy) = sets
            assertTrue("s1" in cancel.getValue("update").jsonObject)
            assertEquals(listOf("e9"), destroy.getValue("destroy").jsonArray.map { it.jsonPrimitive.content })
        }

    private companion object {
        const val RECEIVED = """"receivedAt": "2026-10-31T12:00:00Z""""
    }
}
