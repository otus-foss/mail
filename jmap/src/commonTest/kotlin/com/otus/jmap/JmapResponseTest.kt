package com.otus.jmap

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JmapResponseTest {
    private val method =
        object : JmapMethod<JsonObject>("EmailSubmission/set", JmapCapability.SUBMISSION, "a") {
            override fun arguments() = JsonObject(emptyMap())

            override fun decode(arguments: JsonObject) = arguments
        }
    private val call = JmapRequestBuilder().call(method)

    @Test
    fun implicitResponsesDoNotReplaceTheRequestedResult() {
        val response =
            response(
                """["EmailSubmission/set", {"created":{"draft":{"id":"s1"}}}, "c0"],
            ["Email/set", {"updated":{"e1":null}}, "c0"]""",
            )
        assertEquals(setOf("created"), response[call].keys)
        assertEquals(listOf("EmailSubmission/set", "Email/set"), response.responses(call).map { it.name })
        assertEquals(setOf("updated"), response.responses(call)[1].arguments.keys)
    }

    @Test
    fun implicitErrorsDoNotTurnSuccessfulSubmissionIntoFailure() {
        val response =
            response(
                """["EmailSubmission/set", {"created":{}}, "c0"],
            ["error", {"type":"forbidden"}, "c0"]""",
            )
        assertEquals(setOf("created"), response[call].keys)
        assertEquals("error", response.responses(call)[1].name)
    }

    @Test
    fun explicitErrorsStillFail() {
        val response = response("""["error", {"type":"forbidden"}, "c0"]""")
        assertEquals("forbidden", assertFailsWith<JmapException.MethodError> { response[call] }.type)
    }

    @Test
    fun unexpectedMethodAndMissingCallAreRejected() {
        assertFailsWith<JmapException.NotJmap> { response("""["Email/set", {}, "c0"]""")[call] }
        assertFailsWith<JmapException.NotJmap> { response("""["EmailSubmission/set", {}, "other"]""")[call] }
    }

    private fun response(invocations: String) =
        parseResponse(JmapJson.parseToJsonElement("""{"methodResponses":[$invocations]}""").jsonObject)
}
