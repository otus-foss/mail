package com.otus.mail.feature.compose.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.mail.core.mail.MailFailure
import com.otus.mail.core.messages.data.receivedAt
import com.otus.mail.core.messages.domain.AnsweredMessage
import com.otus.mail.core.messages.domain.Draft
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.SendException
import com.otus.mail.core.messages.domain.SendFailure
import com.otus.mail.core.messages.domain.SendTime
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.feature.inbox.data.at
import com.otus.mail.feature.inbox.data.composeMail
import com.otus.mail.feature.inbox.data.receivedAt
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.STALWART_SESSION
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JmapDraftSenderTest {
    private val login = ADA_LOGIN
    private val calls = mutableListOf<String>()

    /**
     * A server with the identities in [identities] and a Sent folder unless [sent] is false. The
     * submission ends with [submitted], and an `Email/set` after it ends with [tidied].
     */
    private fun sender(
        session: String = SUBMISSION_SESSION,
        identities: String = """{"id": "i1", "email": "ada@example.org"}""",
        sent: Boolean = true,
        submitted: String = """"created": {"send": {"id": "s1"}}""",
        tidied: String = """"updated": {"m1": null}, "destroyed": ["old"]""",
        originalKeywords: String = "{}",
    ): JmapDraftSender {
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(session) { body ->
                calls += body
                when {
                    "Identity/get" in body -> {
                        val sentFolder = if (sent) """, {"id": "se", "role": "sent"}""" else ""
                        responses(
                            """["Mailbox/get", {"state": "m", "list": [{"id": "dr", "role": "drafts"}$sentFolder]}, "c0"]""",
                            """["Identity/get", {"state": "i", "list": [$identities]}, "c1"]""",
                        )
                    }

                    "EmailSubmission/set" in body -> {
                        responses("""["EmailSubmission/set", {"newState": "s2", $submitted}, "c0"]""")
                    }

                    "Email/set" in body && "\"create\"" in body -> {
                        responses("""["Email/set", {"newState": "e2", "created": {"draft": {"id": "e9"}}}, "c0"]""")
                    }

                    "Email/set" in body -> {
                        responses("""["Email/set", {"newState": "e3", $tidied}, "c0"]""")
                    }

                    "Email/get" in body -> {
                        responses(
                            """["Email/get", {"state": "e", "list": [{"id": "m1", $RECEIVED, "keywords": $originalKeywords}]}, "c0"]""",
                        )
                    }

                    else -> {
                        error("Unexpected call: $body")
                    }
                }
            }
        return composeMail(JmapConnections(JmapClient(engine), store)).sender
    }

    private fun responses(vararg items: String) = """{"methodResponses": [${items.joinToString()}]}"""

    private val reply =
        Draft(
            accountId = "ada",
            from = MailSender("Ada Lovelace", "ada@example.org"),
            to = listOf(MailSender("Grace", "grace@example.org")),
            bcc = listOf(MailSender("", "babbage@example.org")),
            subject = "Re: Compiler draft",
            body = "Looks good.",
            emailId = "old",
        )

    @Test
    fun sendingStoresTheDraftThenSubmitsThatCopyByIdAndFilesItInSent() =
        runTest {
            assertTrue(sender().send(reply, AnsweredMessage("m1", forwarded = false), SendTime.Now).isSuccess)
            val (route, store, submit, tidy) = calls.map(::methods)
            assertEquals(listOf("Mailbox/get", "Identity/get"), route.map { it.name })
            assertEquals(listOf("Email/set"), store.map { it.name })
            assertEquals(listOf("EmailSubmission/set"), submit.map { it.name })

            val email = store[0].arguments.at("create", "draft")
            assertEquals(Json.parseToJsonElement("""{"dr": true}"""), email["mailboxIds"])
            assertEquals(
                "babbage@example.org",
                email
                    .getValue("bcc")
                    .jsonArray[0]
                    .jsonObject
                    .text("email"),
            )
            val submission = submit[0].arguments
            val expected = """{"send": {"emailId": "e9", "identityId": "i1"}}"""
            assertEquals(Json.parseToJsonElement(expected), submission["create"])
            val filed = """{"#send": {"mailboxIds/dr": null, "mailboxIds/se": true, "keywords/${'$'}draft": null}}"""
            assertEquals(Json.parseToJsonElement(filed), submission["onSuccessUpdateEmail"])
            assertTrue("envelope" !in submission.at("create", "send"), "The server builds the envelope")

            val (set) = tidy
            assertEquals(
                Json.parseToJsonElement("""{"m1": {"keywords/${'$'}answered": true}}"""),
                set.arguments["update"],
            )
            assertEquals(Json.parseToJsonElement("""["old"]"""), set.arguments["destroy"])
        }

    @Test
    fun aForwardMarksTheOriginalForwardedAndANewMessageNeedsNoTidying() =
        runTest {
            sender().send(reply, AnsweredMessage("m1", forwarded = true), SendTime.Now).getOrThrow()
            val update = methods(calls.last()).single().arguments.at("update", "m1")
            assertEquals(setOf("keywords/${'$'}forwarded"), update.keys)

            calls.clear()
            sender().send(reply.copy(emailId = null), answered = null, SendTime.Now).getOrThrow()
            assertEquals(3, calls.size, "Without a saved copy or an original, nothing is left to tidy")
        }

    @Test
    fun theStoredCopyIsReportedBeforeItIsSubmitted() =
        runTest {
            val reported = mutableListOf<Pair<String, Int>>()
            sender().send(reply, answered = null, SendTime.Now) { reported += it to calls.size }.getOrThrow()
            assertEquals(listOf("e9" to 2), reported, "After the copy is stored, before the submission request")
        }

    @Test
    fun aWildcardIdentitySendsForAnyAddressInItsDomain() =
        runTest {
            val wildcard = """{"id": "w1", "email": "*@EXAMPLE.org"}, {"id": "o1", "email": "ada@other.org"}"""
            sender(identities = wildcard).send(reply, answered = null, SendTime.Now).getOrThrow()
            assertEquals("w1", methods(calls[2])[0].arguments.at("create", "send").text("identityId"))
        }

    @Test
    fun withoutAMatchingIdentityOrSubmissionNothingIsSent() =
        runTest {
            val other = sender(identities = """{"id": "o1", "email": "ada@other.org"}""")
            assertEquals(SendFailure.NoIdentity, other.send(reply, answered = null, SendTime.Now).failure())
            assertEquals(1, calls.size)

            calls.clear()
            assertEquals(SendFailure.NoIdentity, sender(STALWART_SESSION).send(reply, null, SendTime.Now).failure())
            assertEquals(SendFailure.NoIdentity, sender().send(reply.copy(from = null), null, SendTime.Now).failure())
            assertTrue(calls.isEmpty())
        }

    @Test
    fun withoutASentFolderTheSentCopyOnlyLosesDraft() =
        runTest {
            sender(sent = false).send(reply, answered = null, SendTime.Now).getOrThrow()
            val patch = methods(calls[2])[0].arguments.at("onSuccessUpdateEmail", "#send")
            assertEquals(setOf("keywords/${'$'}draft"), patch.keys)
        }

    @Test
    fun aRefusedSubmissionDestroysOnlyTheNewCopyAndReportsWhy() =
        runTest {
            val refused = """"notCreated": {"send": {"type": "invalidRecipients", "invalidRecipients": ["x"]}}"""
            val failure =
                sender(
                    submitted = refused,
                    tidied = """"destroyed": ["e9"]""",
                ).send(reply, null, SendTime.Now).failure()
            assertEquals(SendFailure.Recipients, failure)
            val cleanup = methods(calls.last()).single().arguments
            assertEquals(Json.parseToJsonElement("""["e9"]"""), cleanup["destroy"])
            assertTrue("update" !in cleanup, "An unsent reply does not mark the original")

            val reasons =
                mapOf(
                    "tooManyRecipients" to SendFailure.TooManyRecipients,
                    "tooLarge" to SendFailure.TooLarge,
                    "forbiddenFrom" to SendFailure.Mail(MailFailure.NotPermitted),
                    "forbiddenToSend" to SendFailure.Mail(MailFailure.NotPermitted),
                    "serverFail" to SendFailure.Mail(MailFailure.ServerProblem),
                )
            reasons.forEach { (type, expected) ->
                val error = """"notCreated": {"send": {"type": "$type"}}"""
                assertEquals(
                    expected,
                    sender(
                        submitted = error,
                        tidied = """"destroyed": ["e9"]""",
                    ).send(reply, null, SendTime.Now).failure(),
                )
            }
        }

    private class Call(
        val name: String,
        val arguments: JsonObject,
    )

    private fun methods(body: String): List<Call> =
        Json
            .parseToJsonElement(body)
            .jsonObject
            .getValue("methodCalls")
            .jsonArray
            .map { it.jsonArray }
            .map { Call(it[0].jsonPrimitive.content, it[1].jsonObject) }

    private fun JsonObject.at(vararg keys: String): JsonObject =
        keys.fold(this) { json, key -> json.getValue(key).jsonObject }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun Result<*>.failure(): SendFailure? = (exceptionOrNull() as? SendException)?.failure

    @Test
    fun aScheduledSendHoldsTheMessageAndLeavesItsCopyInDrafts() =
        runTest {
            val at = kotlin.time.Instant.parse("2026-10-19T06:00:00.500Z")
            val sent = sender().send(reply, answered = null, SendTime.Scheduled(at)).getOrThrow()
            assertEquals(SentMessage("ada", "e9", "s1", draftsId = "dr", identityId = "i1"), sent)
            val submission = methods(calls[2])[0].arguments
            val envelope = submission.at("create", "send").getValue("envelope").jsonObject
            val expected =
                """{"mailFrom": {"email": "ada@example.org", "parameters": {"HOLDUNTIL": "2026-10-19T06:00:00Z"}},
                "rcptTo": [{"email": "grace@example.org"}, {"email": "babbage@example.org"}]}"""
            assertEquals(Json.parseToJsonElement(expected), envelope)
            val held = """{"#send": {"keywords/${'$'}draft": null}}"""
            assertEquals(Json.parseToJsonElement(held), submission["onSuccessUpdateEmail"])
        }

    @Test
    fun aSendHeldForUndoWaitsOnTheServerButFilesItsCopyInSentAtOnce() =
        runTest {
            val at = kotlin.time.Instant.parse("2026-10-19T06:00:17Z")
            sender().send(reply, answered = null, SendTime.Held(at)).getOrThrow()
            val submission = methods(calls[2])[0].arguments
            val envelope = submission.at("create", "send", "envelope", "mailFrom", "parameters")
            assertEquals("2026-10-19T06:00:17Z", envelope.text("HOLDUNTIL"))
            val filed = """{"#send": {"mailboxIds/dr": null, "mailboxIds/se": true, "keywords/${'$'}draft": null}}"""
            assertEquals(Json.parseToJsonElement(filed), submission["onSuccessUpdateEmail"])
        }

    @Test
    fun aHeldReplyRemembersTheMarkItAddsSoUndoCanTakeItBack() =
        runTest {
            val at = kotlin.time.Instant.parse("2026-10-19T06:00:17Z")
            val answered = AnsweredMessage("m1", forwarded = false)
            val held = sender().send(reply, answered, SendTime.Held(at)).getOrThrow()
            assertEquals(answered, held.marked)
            assertTrue("Email/get" in calls[3], "The original is looked at before it is marked")

            val marked = """{"${'$'}answered": true}"""
            val before = sender(originalKeywords = marked).send(reply, answered, SendTime.Held(at)).getOrThrow()
            assertNull(before.marked, "A mark the original had before stays")
            assertNull(sender().send(reply, answered, SendTime.Now).getOrThrow().marked)
        }

    private companion object {
        const val RECEIVED = """"receivedAt": "2026-10-31T12:00:00Z""""
    }
}
