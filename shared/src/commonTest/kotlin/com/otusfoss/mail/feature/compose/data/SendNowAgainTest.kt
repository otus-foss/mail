package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.composeMail
import com.otusfoss.mail.testing.perMethodServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sending a held message at once can run again after a lost answer without sending it twice. */
class SendNowAgainTest {
    private val calls = mutableListOf<Pair<String, JsonObject>>()
    private val message = SentMessage(ADA_LOGIN.id, "e", "s", draftsId = "dr", identityId = "i1")

    private fun submitsAgain(call: Pair<String, JsonObject>) =
        call.first == "EmailSubmission/set" && "create" in call.second

    /** A server may destroy a submission once the message went (RFC 8621, section 7). */
    @Test
    fun aHoldTheServerNoLongerKnowsIsNotSubmittedAgain() =
        runTest {
            val connections =
                perMethodServer(calls) { method, arguments ->
                    when (method) {
                        "EmailSubmission/query" -> NO_IDS
                        "EmailSubmission/get" -> """{"state": "s", "list": [], "notFound": []}"""
                        "Email/get" -> """{"state": "e", "list": [$SENT_COPY], "notFound": []}"""
                        "EmailSubmission/set" -> """{"newState": "n", "notUpdated": {"s": {"type": "notFound"}}}"""
                        else -> updated(arguments)
                    }
                }

            val result = composeMail(connections).sender.sendNow(message)

            assertTrue(calls.none(::submitsAgain), "The message may have gone, so it must not go again")
            assertEquals(SendFailure.Unconfirmed, (result.exceptionOrNull() as? SendException)?.failure)
        }

    /** Confirms every update and destroy of an `Email/set`. */
    private fun updated(arguments: JsonObject): String {
        val updated =
            arguments["update"]
                ?.jsonObject
                ?.keys
                .orEmpty()
                .joinToString { """"$it": null""" }
        return """{"newState": "n", "updated": {$updated}, "destroyed": ${arguments["destroy"] ?: "[]"}}"""
    }

    private companion object {
        const val NO_IDS = """{"queryState": "q", "ids": [], "position": 0}"""
        const val SENT_COPY = """{"id": "e", "receivedAt": "2026-11-25T12:00:00Z", "keywords": {}}"""
    }
}
