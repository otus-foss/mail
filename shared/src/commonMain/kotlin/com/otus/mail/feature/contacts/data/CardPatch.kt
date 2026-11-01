package com.otus.mail.feature.contacts.data

import com.otus.jscontact.Card
import com.otus.jscontact.JsContactJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A PatchObject that sets each top-level property that differs between [before] and [after], and
 * removes the ones [after] dropped. Whole properties keep the patch valid on any server, since a
 * path into a property needs its parent to exist there.
 */
internal fun patch(
    before: Card,
    after: Card,
): JsonObject {
    val old = before.json()
    val new = after.json()
    return JsonObject(
        (old.keys + new.keys)
            .filter { it != "id" && old[it] != new[it] }
            .associateWith { new[it] ?: JsonNull },
    )
}

private fun Card.json(): JsonObject = JsContactJson.encodeToJsonElement(Card.serializer(), this).jsonObject
