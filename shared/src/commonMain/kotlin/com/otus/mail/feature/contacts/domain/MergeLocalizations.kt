package com.otus.mail.feature.contacts.domain

import com.otus.jscontact.Card
import com.otus.jscontact.JsContactJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The localizations of [sources] that still fit [merged], the first source with a language
 * winning it. A patch names values by their keys in its own card, and a merge renumbers addresses,
 * re-keys clashes and takes the name from one card, so a patch entry stays only where [merged]
 * holds the value its card held there (RFC 9553, section 2.7.1).
 */
internal fun localizationsFor(
    merged: Card,
    sources: List<Card>,
): Map<String, JsonObject>? {
    val target = merged.json()
    return sources
        .flatMap { source ->
            val own = source.json()
            source.localizations.orEmpty().map { (language, patch) ->
                language to JsonObject(patch.filterKeys { pointer -> own.at(pointer) == target.at(pointer) })
            }
        }.filter { (_, patch) -> patch.isNotEmpty() }
        .distinctBy { (language, _) -> language.lowercase() }
        .toMap()
        .takeIf { it.isNotEmpty() }
}

private fun Card.json(): JsonObject = JsContactJson.encodeToJsonElement(Card.serializer(), this).jsonObject

// A patch changes a property, or one entry of a map-valued property, so those two levels decide.
private fun JsonObject.at(pointer: String): JsonElement? {
    val segments = pointer.split("/").map { it.replace("~1", "/").replace("~0", "~") }
    val value = this[segments.first()]
    return segments.getOrNull(1)?.let { (value as? JsonObject)?.get(it) } ?: value.takeIf { segments.size == 1 }
}
