package com.otus.mail.feature.compose.data

import com.otus.mail.core.mail.mailAccount
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.perMethodServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Filing Scheduled moves each copy by what became of its send. */
class ScheduledFilingTest {
    private val calls = mutableListOf<Pair<String, JsonObject>>()

    /** In Scheduled: one copy whose only send was canceled, one sent, and one another client still sends. */
    private val submissions =
        mapOf(
            "orphan" to listOf(CANCELED),
            "went" to listOf(FINAL),
            "waits" to listOf(CANCELED, PENDING),
        )

    @Test
    fun aCopyWhoseEverySendWasCanceledBecomesADraftAgain() =
        runTest {
            val connections =
                perMethodServer(calls) { method, arguments ->
                    when (method) {
                        "Email/query" -> {
                            val inScheduled = "sc" in arguments.toString()
                            val ids =
                                submissions.keys
                                    .takeIf { inScheduled }
                                    .orEmpty()
                                    .joinToString { "\"$it\"" }
                            """{"queryState": "q", "ids": [$ids], "position": 0}"""
                        }

                        "EmailSubmission/query" -> {
                            """{"queryState": "q", "ids": [], "position": 0}"""
                        }

                        "EmailSubmission/get" -> {
                            val list =
                                submissions.flatMap { (email, statuses) ->
                                    statuses.map {
                                        """{"id": "$email-$it", "emailId": "$email", "undoStatus": "$it"}"""
                                    }
                                }
                            """{"state": "s", "list": [${list.joinToString()}], "notFound": []}"""
                        }

                        else -> {
                            val updated =
                                arguments
                                    .getValue("update")
                                    .jsonObject.keys
                                    .joinToString { "\"$it\": null" }
                            """{"newState": "n", "updated": {$updated}}"""
                        }
                    }
                }
            assertNotNull(
                connections.mailAccount(ADA_LOGIN.id),
            ).fileScheduled(drafts = "dr", scheduled = "sc", sent = "se")
            val update =
                calls
                    .single { it.first == "Email/set" }
                    .second
                    .getValue("update")
                    .jsonObject
            val draft = """{"keywords/${'$'}draft": true, "mailboxIds/sc": null, "mailboxIds/dr": true}"""
            assertEquals(Json.parseToJsonElement(draft), update["orphan"])
            assertEquals(Json.parseToJsonElement("""{"mailboxIds/sc": null, "mailboxIds/se": true}"""), update["went"])
            assertEquals(setOf("orphan", "went"), update.keys, "A send that still goes keeps its copy in Scheduled")
        }
}
