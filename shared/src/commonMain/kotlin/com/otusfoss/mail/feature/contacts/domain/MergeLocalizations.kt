package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.toJsonObject
import com.otusfoss.core.json.types.patchTokens
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val target = merged.toJsonObject()
    return sources
        .flatMap { source ->
            val own = source.toJsonObject()
            source.localizations.orEmpty().map { (language, patch) ->
                language to JsonObject(patch.filterKeys { pointer -> own.sameAt(target, pointer) })
            }
        }.filter { (_, patch) -> patch.isNotEmpty() }
        .distinctBy { (language, _) -> language.lowercase() }
        .toMap()
        .takeIf { it.isNotEmpty() }
}

/** Whether both cards hold the same value at [pointer]; a pointer with a bad escape never matches. */
private fun JsonObject.sameAt(
    other: JsonObject,
    pointer: String,
): Boolean {
    val segments =
        try {
            patchTokens(pointer)
        } catch (expected: IllegalArgumentException) {
            return false
        }
    return at(segments) == other.at(segments)
}

// A patch changes a property, or one entry of a map-valued property, so those two levels decide.
private fun JsonObject.at(segments: List<String>): JsonElement? {
    val value = this[segments.first()]
    return segments.getOrNull(1)?.let { (value as? JsonObject)?.get(it) } ?: value.takeIf { segments.size == 1 }
}
