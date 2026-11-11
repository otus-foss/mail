package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SendTime
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.SUBMISSION_SESSION
import com.otusfoss.mail.testing.composeMail
import com.otusfoss.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a send does with its stored copy when the submission does not go as asked. */
class JmapDraftSenderRecoveryTest {
    private val sets = mutableListOf<JsonObject>()

    private val draft =
        Draft(
            accountId = ADA_LOGIN.id,
            from = MailSender("Ada Lovelace", "ada@example.org"),
            to = listOf(MailSender("Grace", "grace@example.org")),
            subject = "Compiler draft",
            body = "Looks good.",
        )

    /** A server that stores the copy as `e9` and answers the submission with [submitted]. */
    private fun sender(submitted: (callId: String) -> String): JmapDraftSender {
        val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
        val engine =
            stalwartLike(SUBMISSION_SESSION) { body ->
                val responses =
                    Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                        val (name, arguments, id) = call.jsonArray
                        answer(name.jsonPrimitive.content, arguments.jsonObject, id.toString(), submitted)
                    }
                """{"methodResponses": [${responses.joinToString()}]}"""
            }
        return composeMail(JmapConnections(JmapClient(engine), store)).sender
    }

    private fun answer(
        method: String,
        arguments: JsonObject,
        id: String,
        submitted: (callId: String) -> String,
    ): String =
        when (method) {
            "Mailbox/get" -> """["$method", {"state": "m", "list": $FOLDERS}, $id]"""
            "Identity/get" -> """["$method", {"state": "i", "list": [{"id": "i1", "email": "ada@example.org"}]}, $id]"""
            "Email/set" -> """["$method", ${emailSet(arguments)}, $id]"""
            "EmailSubmission/set" -> submitted(id)
            else -> error("Unexpected call: $method")
        }

    private fun emailSet(arguments: JsonObject): JsonObject {
        sets += arguments
        return buildJsonObject {
            put("newState", "e2")
            if ("create" in arguments) put("created", Json.parseToJsonElement("""{"draft": {"id": "e9"}}"""))
            arguments["update"]?.let { update -> put("updated", JsonObject(update.jsonObject.mapValues { JsonNull })) }
            arguments["destroy"]?.let { put("destroyed", it) }
        }
    }

    private fun Result<*>.failure(): SendFailure? = (exceptionOrNull() as? SendException)?.failure

    private fun destroyed(): List<JsonArray> = sets.mapNotNull { it["destroy"]?.jsonArray }

    @Test
    fun aServerFailingForNowKeepsTheCopyForTheOutboxToLookUp() =
        runTest {
            listOf("serverUnavailable", "serverFail", "serverPartialFail").forEach { type ->
                sets.clear()
                val reported = mutableListOf<String>()
                val failure =
                    sender { """["error", {"type": "$type"}, $it]""" }
                        .send(draft, answered = null, SendTime.Now) { reported += it }
                        .failure()
                assertEquals(SendFailure.Mail(MailFailure.ServerProblem), failure)
                assertEquals(listOf("e9"), reported, "The outbox holds the copy's id")
                assertTrue(destroyed().isEmpty(), "A $type copy stays until the outbox looks it up")
            }
        }

    @Test
    fun aSubmissionTheServerRejectsDestroysTheCopy() =
        runTest {
            sender { """["error", {"type": "invalidArguments"}, $it]""" }.send(draft, answered = null, SendTime.Now)
            assertEquals(listOf(Json.parseToJsonElement("""["e9"]""")), destroyed())
        }

    @Test
    fun aCopyTheServerLeftInDraftsIsFiledAgain() =
        runTest {
            val left = """["Email/set", {"newState": "e3", "notUpdated": {"e9": {"type": "serverFail"}}}"""
            sender { """$SUBMITTED, $it], $left, $it]""" }.send(draft, answered = null, SendTime.Now).getOrThrow()
            val filed = """{"e9": {"mailboxIds/dr": null, "mailboxIds/se": true, "keywords/${'$'}draft": null}}"""
            assertEquals(Json.parseToJsonElement(filed), sets.last()["update"])
        }

    @Test
    fun aCopyTheServerFiledNeedsNoRepair() =
        runTest {
            val filed = """["Email/set", {"newState": "e3", "updated": {"e9": null}}"""
            sender { """$SUBMITTED, $it], $filed, $it]""" }.send(draft, answered = null, SendTime.Now).getOrThrow()
            assertTrue(sets.none { "update" in it })
        }

    private companion object {
        const val SUBMITTED = """["EmailSubmission/set", {"newState": "s2", "created": {"send": {"id": "s1"}}}"""
        const val FOLDERS = """[{"id": "dr", "role": "drafts"}, {"id": "se", "role": "sent"}]"""
    }
}
