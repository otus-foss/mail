package com.otus.mail.core.mail

import com.otus.jmap.JmapSort
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** `Email/query` order with the most recently received mail first (RFC 8621, section 4.4.2). */
internal val NEWEST_FIRST = listOf(JmapSort("receivedAt", isAscending = false))

/** The `Email/query` filter for the mail in mailbox [id] (RFC 8621, section 4.4.1). */
internal fun inMailbox(id: String): JsonObject = condition("inMailbox", JsonPrimitive(id))

/** One `FilterCondition` property, such as `{"from": "jana@example.com"}` (RFC 8621, section 4.4.1). */
internal fun condition(
    name: String,
    value: JsonElement,
): JsonObject = buildJsonObject { put(name, value) }

/** A `FilterOperator` (RFC 8620, section 5.5) such as `AND`, `OR`, or `NOT` over [conditions]. */
internal fun operator(
    name: String,
    conditions: List<JsonObject>,
): JsonObject =
    buildJsonObject {
        put("operator", JsonPrimitive(name))
        put("conditions", JsonArray(conditions))
    }

/** One condition as itself; several under `OR`. */
internal fun anyOf(conditions: List<JsonObject>): JsonObject = conditions.singleOrNull() ?: operator("OR", conditions)
