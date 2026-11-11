package com.otusfoss.mail.testing

import com.otusfoss.core.jmap.JmapClient
import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.login.InMemoryLoginStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Connections to a [stalwartLike] server, signed in as [ADA_LOGIN], that answers each method call
 * with the arguments [answer] gives for it and records the call in [calls]. An [answer] that throws
 * loses the answer to the whole request, as a dropped connection does.
 */
internal fun perMethodServer(
    calls: MutableList<Pair<String, JsonObject>>,
    answer: (method: String, arguments: JsonObject) -> String,
): JmapConnections {
    val store = InMemoryLoginStore().apply { save(ADA_LOGIN, "secret") }
    val engine =
        stalwartLike(SUBMISSION_SESSION) { body ->
            val responses =
                Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
                    val (name, arguments, id) = call.jsonArray
                    val method = name.jsonPrimitive.content
                    calls += method to arguments.jsonObject
                    """["$method", ${answer(method, arguments.jsonObject)}, $id]"""
                }
            """{"methodResponses": [${responses.joinToString()}]}"""
        }
    return JmapConnections(JmapClient(engine), store)
}
