package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapClient
import com.otus.jmap.JmapConnections
import com.otus.jmap.login.InMemoryLoginStore
import com.otus.jmap.login.JmapLogin
import com.otus.mail.feature.inbox.domain.DraftRestoreException
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.testing.SUBMISSION_SESSION
import com.otus.mail.testing.stalwartLike
import kotlinx.coroutines.test.runTest
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
            assertTrue(sender(calls, canceled = false).sendNow(message).isSuccess, "It still goes at its time")
            assertEquals(1, calls.size)
        }

    @Test
    fun aMissingCancellationAcknowledgementDoesNotRestoreTheDraft() =
        runTest {
            val calls = mutableListOf<String>()
            assertTrue(sender(calls, acknowledged = false).cancel(message).isFailure)
            assertEquals(1, calls.size)
        }

    private fun sender(
        calls: MutableList<String>,
        canceled: Boolean = true,
        restored: Boolean = true,
        acknowledged: Boolean = true,
    ): JmapDraftSender {
        val login = JmapLogin("a", "https://mail.example.org/.well-known/jmap", "ada@example.org", "Ada")
        val store = InMemoryLoginStore().apply { save(login, "secret") }
        val engine =
            stalwartLike(SUBMISSION_SESSION) { body ->
                calls += body
                val cancel = "EmailSubmission/set" in body
                val method = if (cancel) "EmailSubmission/set" else "Email/set"
                val id = if (cancel) "s" else "e"
                val result =
                    when {
                        """"create"""" in body -> """, "created": {"send": {"id": "s2"}}"""
                        cancel && !acknowledged -> ""
                        if (cancel) canceled else restored -> """, "updated": {"$id": null}"""
                        else -> """, "notUpdated": {"$id": {"type": "forbidden"}}"""
                    }
                """{"methodResponses": [["$method", {"newState": "n"$result}, "c0"]]}"""
            }
        return JmapDraftSender(JmapConnections(JmapClient(engine), store), store)
    }

    private val message = SentMessage("a", "e", "s")
}
