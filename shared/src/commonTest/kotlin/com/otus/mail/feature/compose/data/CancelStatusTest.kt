package com.otus.mail.feature.compose.data

import com.otus.mail.core.messages.domain.SendCancel
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.testing.ADA_LOGIN
import com.otus.mail.testing.composeMail
import com.otus.mail.testing.perMethodServer
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A cancel the server does not confirm is settled by what the server now knows of the copy. */
class CancelStatusTest {
    private val message = SentMessage(ADA_LOGIN.id, "e", "s", draftsId = "dr")
    private val calls = mutableListOf<Pair<String, JsonObject>>()

    @Test
    fun aCancelWhoseAnswerWasLostButThatLandedRestoresTheDraft() =
        runTest {
            assertEquals(SendCancel.Stopped, cancel(refusal = null, statuses = listOf(CANCELED)))
            assertTrue(restored(), "The copy would sit in Scheduled with nothing to send it")
        }

    @Test
    fun aRepeatedCancelThatTheServerRefusesStillStops() =
        runTest {
            assertEquals(SendCancel.Stopped, cancel(refusal = "cannotUnsend", statuses = listOf(CANCELED)))
            assertTrue(restored())
        }

    @Test
    fun aSendThatWentIsFinal() =
        runTest {
            assertEquals(SendCancel.Final, cancel(statuses = listOf(FINAL)))
            assertEquals(SendCancel.Final, cancel(refusal = "cannotUnsend", statuses = listOf(PENDING)))
            assertFalse(restored(), "A sent copy stays as it is")
        }

    @Test
    fun aSendThatStillWaitsIsWaitingEvenWhenItsOwnSubmissionWasCanceled() =
        runTest {
            assertEquals(SendCancel.Waiting, cancel(statuses = listOf(PENDING)))
            assertEquals(SendCancel.Waiting, cancel(statuses = listOf(CANCELED, PENDING)), "Another client sends it")
            assertFalse(restored())
        }

    @Test
    fun aSendTheServerForgotIsUnknownUnlessItsCopyIsADraftAgain() =
        runTest {
            assertEquals(SendCancel.Unknown, cancel(statuses = emptyList()))
            assertEquals(SendCancel.Stopped, cancel(statuses = emptyList(), copyIsDraft = true))
            assertFalse(restored(), "Someone made it a draft already")
        }

    @Test
    fun aStatusTheServerCannotGiveLeavesTheSendWaiting() =
        runTest {
            assertEquals(SendCancel.Waiting, cancel(refusal = null, statuses = null))
            assertFalse(restored())
        }

    /**
     * Cancels [message] on a server that refuses with [refusal], or loses its answer when it is
     * null, and whose copy has submissions with [statuses], or whose status answer is lost.
     */
    private suspend fun cancel(
        refusal: String? = "forbidden",
        statuses: List<String>? = emptyList(),
        copyIsDraft: Boolean = false,
    ): SendCancel {
        val connections =
            perMethodServer(calls) { method, _ ->
                when (method) {
                    "EmailSubmission/set" -> {
                        if (refusal == null) throw IOException("The answer was lost")
                        """{"newState": "n", "notUpdated": {"s": {"type": "$refusal"}}}"""
                    }

                    "EmailSubmission/query" -> {
                        if (statuses == null) throw IOException("The answer was lost")
                        """{"queryState": "q", "ids": [${statuses.indices.joinToString {
                            "\"s$it\""
                        }}], "position": 0}"""
                    }

                    "EmailSubmission/get" -> {
                        val list = statuses.orEmpty().mapIndexed { i, it -> """{"id": "s$i", "undoStatus": "$it"}""" }
                        """{"state": "s", "list": [${list.joinToString()}], "notFound": []}"""
                    }

                    "Email/get" -> {
                        val keywords = if (copyIsDraft) """{"${'$'}draft": true}""" else "{}"
                        val copy = """{"id": "e", "keywords": $keywords, "receivedAt": "2026-11-10T12:00:00Z"}"""
                        """{"state": "e", "list": [$copy], "notFound": []}"""
                    }

                    else -> {
                        """{"newState": "n", "updated": {"e": null}}"""
                    }
                }
            }
        return composeMail(connections).sender.cancel(message)
    }

    private fun restored() = calls.any { it.first == "Email/set" }
}
