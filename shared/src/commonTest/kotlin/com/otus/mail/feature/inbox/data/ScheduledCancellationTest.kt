package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.AnsweredMessage
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.SendException
import com.otus.mail.feature.inbox.domain.SendFailure
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduledCancellationTest {
    @Test
    fun aRefusedCancellationDoesNotRestoreTheEmailAsADraft() =
        runTest {
            val calls = mutableListOf<String>()
            val result = sender(calls, canceled = false).cancel(message)
            assertTrue(result.isFailure)
            assertEquals(1, calls.size)
            assertTrue("EmailSubmission/set" in calls.single())
        }

    @Test
    fun cancellationAndDraftRestorationMustBothSucceed() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls).cancel(message).isSuccess)
            assertEquals(2, calls.size)
            assertTrue("EmailSubmission/set" in calls.first())
            assertTrue("Email/set" in calls.last())

            calls.clear()
            assertTrue(sender(calls, restored = false).cancel(message).exceptionOrNull() is DraftRestoreException)
            assertEquals(2, calls.size)
        }

    @Test
    fun aCanceledCopyGoesBackToDraftsAsADraft() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls).cancel(message.copy(draftsId = "dr")).isSuccess)
            val update =
                Json
                    .parseToJsonElement(calls.last())
                    .jsonObject
                    .getValue("methodCalls")
                    .jsonArray
                    .single()
                    .jsonArray[1]
                    .jsonObject
                    .getValue("update")
                    .jsonObject
                    .getValue("e")
            val expected = """{"keywords/${'$'}draft": true, "mailboxIds": {"dr": true}}"""
            assertEquals(Json.parseToJsonElement(expected), update)
        }

    @Test
    fun aCanceledReplyTakesBackTheMarkItsSendAdded() =
        runTest {
            val calls = mutableListOf<String>()
            val answered = message.copy(marked = AnsweredMessage("o1", forwarded = true))
            assertTrue(sender(calls, alsoUpdated = "o1").cancel(answered).isSuccess)
            val update =
                Json
                    .parseToJsonElement(calls.last())
                    .jsonObject
                    .getValue("methodCalls")
                    .jsonArray
                    .single()
                    .jsonArray[1]
                    .jsonObject
                    .getValue("update")
                    .jsonObject
            assertEquals(Json.parseToJsonElement("""{"keywords/${'$'}forwarded": null}"""), update.getValue("o1"))
        }

    @Test
    fun sendingNowCancelsTheHoldThenSubmitsTheSameCopyAgain() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls).sendNow(message.copy(identityId = "i1")).isSuccess)
            assertEquals(2, calls.size)
            assertTrue("canceled" in calls.first() && "create" !in calls.first(), "The hold is canceled first")
            val create =
                Json
                    .parseToJsonElement(calls.last())
                    .jsonObject
                    .getValue("methodCalls")
                    .jsonArray
                    .single()
                    .jsonArray[1]
                    .jsonObject
                    .getValue("create")
            assertEquals(Json.parseToJsonElement("""{"send": {"emailId": "e", "identityId": "i1"}}"""), create)
        }

    @Test
    fun aHoldTheServerKeepsIsNotSentAgain() =
        runTest {
            val calls = mutableListOf<String>()
            val result = sender(calls, canceled = false, submitted = true).sendNow(message)
            assertTrue(result.isSuccess, "It still goes at its time")
            assertTrue(calls.none { "create" in it })
        }

    @Test
    fun aLostCancelAnswerWithTheServerOutOfReachSubmitsNothing() =
        runTest {
            val calls = mutableListOf<String>()
            val result = sender(calls, lost = setOf(Lost.Cancel, Lost.Query)).sendNow(message)
            assertEquals(SendFailure.Unreachable, (result.exceptionOrNull() as SendException).failure)
            assertTrue(calls.isEmpty(), "A later run decides once the server can say")
        }

    @Test
    fun aHoldCanceledByALostRequestIsSubmittedAgain() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls, lost = setOf(Lost.Cancel)).sendNow(message).isSuccess)
            assertTrue("EmailSubmission/query" in calls.first(), "The server says no submission is left")
            assertTrue("\"create\"" in calls.last())
        }

    @Test
    fun aMissingCancellationAcknowledgementDoesNotRestoreTheDraft() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls, acknowledged = false).cancel(message).isFailure)
            assertEquals(1, calls.size)
        }

    @Test
    fun aLostResubmissionAnswerRestoresTheDraftWhenNothingWasSubmitted() =
        runTest {
            val calls = mutableListOf<String>()
            val result = sender(calls, lost = setOf(Lost.Resend)).sendNow(message.copy(draftsId = "dr"))
            assertTrue(result.isFailure)
            assertTrue("EmailSubmission/query" in calls[1], "The server is asked before the draft comes back")
            assertTrue("Email/set" in calls.last() && "keywords/" in calls.last(), "The copy is a draft again")
        }

    @Test
    fun aLostResubmissionAnswerKeepsAMessageTheServerTook() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls, lost = setOf(Lost.Resend), submitted = true).sendNow(message).isSuccess)
            assertTrue(calls.none { "keywords" in it }, "A message on its way stays in Sent")
        }

    private fun sender(
        calls: MutableList<String>,
        canceled: Boolean = true,
        restored: Boolean = true,
        acknowledged: Boolean = true,
        lost: Set<Lost> = emptySet(),
        submitted: Boolean = false,
        alsoUpdated: String? = null,
    ): JmapDraftSender {
        val login = JmapLogin("a", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(SUBMISSION_SESSION) { body ->
                val kind =
                    when {
                        "EmailSubmission/query" in body -> Lost.Query
                        """"create"""" in body -> Lost.Resend
                        "EmailSubmission/set" in body -> Lost.Cancel
                        else -> null
                    }
                if (kind in lost) throw IOException("The answer was lost")
                calls += body
                if ("EmailSubmission/query" in body) {
                    val ids = if (submitted) """["s2"]""" else "[]"
                    val found = """{"queryState": "q", "ids": $ids, "position": 0}"""
                    return@stalwartLike """{"methodResponses": [["EmailSubmission/query", $found, "c0"]]}"""
                }
                val cancel = "EmailSubmission/set" in body
                val method = if (cancel) "EmailSubmission/set" else "Email/set"
                val id = if (cancel) "s" else "e"
                val result =
                    when {
                        """"create"""" in body -> """, "created": {"send": {"id": "s2"}}"""
                        cancel && !acknowledged -> ""
                        if (cancel) canceled else restored -> """, "updated": {"$id": null${also(alsoUpdated)}}"""
                        else -> """, "notUpdated": {"$id": {"type": "forbidden"}}"""
                    }
                """{"methodResponses": [["$method", {"newState": "n"$result}, "c0"]]}"""
            }
        return JmapDraftSender(JmapConnections(JmapClient(engine), store), store)
    }

    private fun also(id: String?) = id?.let { """, "$it": null""" }.orEmpty()

    private val message = SentMessage("a", "e", "s")

    /** Requests whose answers the fake server loses. */
    private enum class Lost { Cancel, Resend, Query }
}
