package com.otus.mail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The response to the JMAP API request [body]: each method call answered in order by [respond],
 * with `#ids` back-references resolved against earlier results (RFC 8620, section 3.7). A call
 * [respond] answers with null, or whose reference cannot be resolved, gets a method error:
 * `cannotCalculateChanges` for `/changes` and `serverFail` for the rest.
 */
internal fun jmapReply(
    body: String,
    respond: (method: String, arguments: JsonObject) -> JsonObject?,
): String {
    val results = mutableMapOf<String, JsonObject>()
    val responses =
        Json.parseToJsonElement(body).jsonObject.getValue("methodCalls").jsonArray.map { call ->
            val (name, arguments, id) = call.jsonArray
            val method = name.jsonPrimitive.content
            val result = resolved(arguments.jsonObject, results)?.let { respond(method, it) }
            if (result != null) results[id.jsonPrimitive.content] = result
            JsonArray(listOf(JsonPrimitive(if (result == null) "error" else method), result ?: refusal(method), id))
        }
    return buildJsonObject { put("methodResponses", JsonArray(responses)) }.toString()
}

/** A `/get` result in [state] with [list]. */
internal fun listResult(
    state: String,
    list: List<JsonElement>,
): JsonObject =
    buildJsonObject {
        put("state", JsonPrimitive(state))
        put("list", JsonArray(list))
    }

/** The call's `ids`, explicit or resolved from a back-reference; null when it asks for all. */
internal fun JsonObject.ids(): List<String>? = (get("ids") as? JsonArray)?.strings()

internal fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }

internal fun List<String>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

internal val JsonObject.id: String get() = getValue("id").jsonPrimitive.content

private fun refusal(method: String): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive(if (method.endsWith("/changes")) "cannotCalculateChanges" else "serverFail"))
    }

/** [arguments] with `#ids` replaced by the result it points to; null when that call failed. */
private fun resolved(
    arguments: JsonObject,
    results: Map<String, JsonObject>,
): JsonObject? {
    val reference = arguments["#ids"]?.jsonObject ?: return arguments
    val path =
        reference
            .getValue("path")
            .jsonPrimitive.content
            .trim('/')
            .split('/')
    return results[reference.getValue("resultOf").jsonPrimitive.content]?.let { source ->
        val ids =
            path
                .fold(listOf<JsonElement>(source)) { elements, step ->
                    elements.flatMap {
                        if (step == "*") it.jsonArray.toList() else listOfNotNull((it as? JsonObject)?.get(step))
                    }
                }.flatMap { if (it is JsonArray) it.toList() else listOf(it) }
        JsonObject(arguments - "#ids" + ("ids" to JsonArray(ids)))
    }
}
