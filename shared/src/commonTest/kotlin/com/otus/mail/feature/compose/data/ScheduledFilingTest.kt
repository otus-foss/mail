package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduledFilingTest {
    private val calls = mutableListOf<String>()

    @Test
    fun mailAnotherClientScheduledMovesFromSentToScheduled() =
        runTest {
            val pending = listOf(Pending("web", NOW + 2.days, "se"), Pending("held", NOW + 20.seconds, "se"))
            file(pending, scheduled = "sc")
            assertEquals(mapOf("web" to moved("se", "sc")), updates(), "A send held for Undo stays in Sent")
        }

    @Test
    fun aScheduledCopyInDraftsOrSentMakesTheScheduledFolder() =
        runTest {
            file(listOf(Pending("web", NOW + 2.days, "dr")), scheduled = null)
            assertTrue(calls.any { "Mailbox/set" in it })
            assertEquals(mapOf("web" to moved("dr", "made")), updates())
        }

    @Test
    fun nothingScheduledMakesNoFolderAndMovesNothing() =
        runTest {
            file(listOf(Pending("held", NOW + 20.seconds, "se")), scheduled = null)
            assertTrue(calls.none { "Mailbox/set" in it || "Email/set" in it })
        }

    @Test
    fun aCopyAlreadyInScheduledStaysAndASentOneMovesToSent() =
        runTest {
            file(listOf(Pending("waits", NOW + 2.days, "sc")), scheduled = "sc", inScheduled = listOf("waits", "gone"))
            assertEquals(mapOf("gone" to moved("sc", "se")), updates())
        }

    /** One pending submission of [emailId], sent at [at], whose copy is in [mailbox]. */
    private class Pending(
        val emailId: String,
        val at: Instant,
        val mailbox: String,
    )

    /**
     * Files Ada's scheduled mail on a server whose pending submissions are [pending] and whose
     * Scheduled folder, when there is one, is [scheduled] and holds [inScheduled]. Of those, only
     * a [pending] one waits: the others' sends are final.
     */
    private suspend fun file(
        pending: List<Pending>,
        scheduled: String?,
        inScheduled: List<String> = emptyList(),
    ) {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike(SUBMISSION_SESSION) { body ->
                calls += body
                when {
                    "Email/get" in body -> {
                        val submissions =
                            pending.joinToString {
                                """{"emailId": "${it.emailId}", "sendAt": "${it.at}"}"""
                            }
                        val copies =
                            pending.joinToString {
                                """{"id": "${it.emailId}", "mailboxIds": {"${it.mailbox}": true}, "receivedAt": "$NOW"}"""
                            }
                        responses(
                            """["EmailSubmission/query", {"queryState": "q", "ids": [], "position": 0}, "c0"]""",
                            """["EmailSubmission/get", {"state": "s", "list": [$submissions]}, "c1"]""",
                            """["Email/get", {"state": "e", "list": [$copies]}, "c2"]""",
                        )
                    }

                    "EmailSubmission/query" in body -> {
                        val waiting = pending.map { it.emailId }
                        val status =
                            inScheduled.joinToString {
                                val undo = if (it in waiting) "pending" else "final"
                                """{"emailId": "$it", "undoStatus": "$undo"}"""
                            }
                        responses(
                            """["EmailSubmission/query", {"queryState": "q", "ids": [], "position": 0}, "c0"]""",
                            """["EmailSubmission/get", {"state": "s", "list": [$status]}, "c1"]""",
                        )
                    }

                    "Email/query" in body -> {
                        val ids = if (""""inMailbox":"sc"""" in body) inScheduled else emptyList()
                        val found = ids.joinToString { "\"$it\"" }
                        responses("""["Email/query", {"queryState": "q", "ids": [$found], "position": 0}, "c0"]""")
                    }

                    "Mailbox/set" in body -> {
                        responses(
                            """["Mailbox/set", {"newState": "m2", "created": {"scheduled": {"id": "made"}}}, "c0"]""",
                        )
                    }

                    "Email/set" in body -> {
                        val updated = updatesIn(body).keys.joinToString { "\"$it\": null" }
                        responses("""["Email/set", {"newState": "e2", "updated": {$updated}}, "c0"]""")
                    }

                    else -> {
                        error("Unexpected call: $body")
                    }
                }
            }
        JmapConnections(JmapClient(engine), store)
            .mailAccount(ADA_LOGIN)
            .fileScheduled(drafts = "dr", scheduled = scheduled, sent = "se", now = NOW)
    }

    /** The email updates of the one `Email/set` the filing sent; none when it sent none. */
    private fun updates(): Map<String, JsonElement> =
        calls
            .filter { "Email/set" in it }
            .map(::updatesIn)
            .fold(emptyMap()) { all, update -> all + update }

    private fun updatesIn(body: String): Map<String, JsonElement> =
        Json
            .parseToJsonElement(body)
            .jsonObject
            .getValue("methodCalls")
            .jsonArray
            .single()
            .jsonArray[1]
            .jsonObject
            .getValue("update")
            .jsonObject

    private fun moved(
        from: String,
        to: String,
    ): JsonElement = Json.parseToJsonElement("""{"mailboxIds/$from": null, "mailboxIds/$to": true}""")

    private fun responses(vararg items: String) = """{"methodResponses": [${items.joinToString()}]}"""

    private companion object {
        val NOW = Instant.parse("2026-11-10T12:00:00Z")
    }
}
