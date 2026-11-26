package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.core.messages.domain.SendException
import com.otusfoss.mail.core.messages.domain.SendFailure
import com.otusfoss.mail.core.messages.domain.SentMessage
import com.otusfoss.mail.testing.ADA_LOGIN
import com.otusfoss.mail.testing.composeMail
import com.otusfoss.mail.testing.perMethodServer
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sending a held message at once can run again after a lost answer without sending it twice. */
class SendNowAgainTest {
    private val calls = mutableListOf<Pair<String, JsonObject>>()
    private val message = SentMessage(ADA_LOGIN.id, "e", "s", draftsId = "dr", identityId = "i1", sentId = "se")

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
            assertEquals(SendFailure.Unconfirmed, result.failure())
        }

    /**
     * The first try cancels the hold and loses the new submission, so the copy becomes a draft in
     * Drafts again and the try fails as unreachable. The second try submits that copy.
     */
    @Test
    fun aCopySubmittedAfterItBecameADraftAgainIsFiledAsSent() =
        runTest {
            var createLost = true
            var cancels = 0

            fun submitted(): String {
                if (!createLost) return """{"newState": "n", "created": {"send": {"id": "s2"}}}"""
                createLost = false
                throw IOException("The request was lost")
            }
            val connections =
                perMethodServer(calls) { method, arguments ->
                    when {
                        // Only the canceled hold is left: no submission that nobody canceled.
                        method == "EmailSubmission/query" -> if ("NOT" in arguments.toString()) NO_IDS else HOLD_ID

                        method == "EmailSubmission/get" -> CANCELED_HOLD

                        method == "Email/get" -> """{"state": "e", "list": [$DRAFT_COPY], "notFound": []}"""

                        method == "EmailSubmission/set" && "create" in arguments -> submitted()

                        // A second cancel of a canceled submission is refused.
                        method == "EmailSubmission/set" && cancels++ == 0 -> CANCELED

                        method == "EmailSubmission/set" -> CANNOT_UNSEND

                        else -> updated(arguments)
                    }
                }
            val sender = composeMail(connections).sender

            val first = sender.sendNow(message)
            assertEquals(SendFailure.Mail(MailFailure.Unreachable), first.failure())
            val restore = calls.indexOfLast { (method, patch) -> method == "Email/set" && DRAFT in patch.toString() }
            assertTrue(restore >= 0, "The first try made the copy a draft in Drafts again")

            assertTrue(sender.sendNow(message).isSuccess, "The second try sends it")
            val filed =
                calls
                    .drop(restore + 1)
                    .single(::submitsAgain)
                    .second
                    .getValue("onSuccessUpdateEmail")
            val expected = """{"#send": {"mailboxIds/dr": null, "mailboxIds/se": true, "keywords/$DRAFT": null}}"""
            assertEquals(Json.parseToJsonElement(expected), filed, "The sent copy leaves Drafts and is no draft")
        }

    private fun Result<*>.failure(): SendFailure? = (exceptionOrNull() as? SendException)?.failure

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
        const val HOLD_ID = """{"queryState": "q", "ids": ["s"], "position": 0}"""
        const val CANCELED_HOLD = """{"state": "s", "list": [{"id": "s", "undoStatus": "canceled"}], "notFound": []}"""
        const val CANCELED = """{"newState": "n", "updated": {"s": null}}"""
        const val CANNOT_UNSEND = """{"newState": "n", "notUpdated": {"s": {"type": "cannotUnsend"}}}"""
        const val DRAFT = "\$draft"
        const val DRAFT_COPY = """{"id": "e", "receivedAt": "2026-11-25T12:00:00Z", "keywords": {"$DRAFT": true}}"""
    }
}
