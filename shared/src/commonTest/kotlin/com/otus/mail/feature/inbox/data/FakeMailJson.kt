package com.otus.mail.feature.inbox.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Arguments with `#ids` replaced by the earlier result it refers to (RFC 8620, section 3.7); null
 * when that call failed, which the server answers with an error.
 */
internal fun resolved(
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

/** The mailboxes a list filter names: an AND's first condition holds them, and an OR lists them. */
internal fun mailboxesIn(filter: JsonObject): Set<String> {
    val conditions = filter["conditions"]?.jsonArray.orEmpty().map { it.jsonObject }
    return when {
        "inMailbox" in filter -> setOf(filter.getValue("inMailbox").jsonPrimitive.content)
        filter["operator"] == JsonPrimitive("AND") -> mailboxesIn(conditions.first())
        else -> conditions.flatMap(::mailboxesIn).toSet()
    }
}

/** `Mailbox/changes` of a server whose mailboxes never change, so every state is the current one. */
internal fun mailboxChanges(arguments: JsonObject) =
    buildJsonObject {
        put("oldState", arguments.getValue("sinceState"))
        put("newState", JsonPrimitive("m"))
        put("hasMoreChanges", JsonPrimitive(false))
        put("created", JsonArray(emptyList()))
        put("updated", JsonArray(emptyList()))
        put("destroyed", JsonArray(emptyList()))
    }

/** [path] is a property, which [value] replaces, or `property/key`, which it sets or clears. */
internal fun JsonObject.at(
    path: String,
    value: JsonElement,
): JsonObject {
    if ('/' !in path) return JsonObject(this + (path to value))
    val property = path.substringBefore('/')
    val key = path.substringAfter('/')
    val inner = (get(property) as? JsonObject).orEmpty()
    return JsonObject(this + (property to JsonObject(if (value is JsonNull) inner - key else inner + (key to value))))
}

internal fun JsonObject.only(properties: Set<String>?): JsonObject =
    if (properties == null) this else JsonObject(filterKeys { it in properties })

internal fun JsonObject.ids(): List<String>? = (get("ids") as? JsonArray)?.strings()

internal fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }

internal val JsonElement.id: String get() = jsonObject.getValue("id").jsonPrimitive.content
internal val JsonObject.threadId: String get() = get("threadId")?.jsonPrimitive?.content ?: id
internal val JsonObject.receivedAt: String get() = getValue("receivedAt").jsonPrimitive.content
internal val JsonObject.boxes: Set<String> get() = (get("mailboxIds") as? JsonObject)?.keys.orEmpty()

/** A list's filter and sort, which name the results a query state stands for. */
internal val JsonObject.listKey: String get() = "${get("filter")}|${get("sort")}"
