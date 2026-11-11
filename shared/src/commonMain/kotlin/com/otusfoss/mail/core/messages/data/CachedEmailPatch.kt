package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.core.json.types.patchTokens
import com.otusfoss.mail.core.cache.CacheKind
import com.otusfoss.mail.core.cache.MailCache
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * Applies `Email/set` [patches] the server accepted (RFC 8620, section 5.3) to the cached copies
 * of those emails, so the lists match before the next sync reads the change back. Only the paths
 * marks and moves send are understood: a property, or one key below it such as `keywords/$seen`.
 */
internal suspend fun MailCache.patchEmails(
    loginId: String,
    patches: Map<String, JsonObject>,
) {
    if (patches.isEmpty()) return
    val held = PATCHED_KINDS.associateWith { read(loginId, it, patches.keys) }
    write(loginId) {
        held.forEach { (kind, objects) ->
            objects.forEach { (id, json) ->
                val email = patched(json, patches.getValue(id))
                if (email != null) put(kind, id, email.toString(), email.receivedMillis)
            }
        }
    }
}

private fun patched(
    json: String,
    patch: JsonObject,
): JsonObject? =
    try {
        patch.entries.fold(JmapJson.parseToJsonElement(json).jsonObject) { email, (path, value) ->
            email.patchedAt(path, value)
        }
    } catch (expected: SerializationException) {
        null
    } catch (expected: IllegalArgumentException) {
        null
    }

/** [path] is `property` or `property/key`; a bad escape throws [IllegalArgumentException]. */
internal fun JsonObject.patchedAt(
    path: String,
    value: JsonElement,
): JsonObject {
    val tokens = patchTokens(path)
    val property = tokens.first()
    val key = tokens.getOrNull(1) ?: return JsonObject(this + (property to value))
    val inner = (get(property) as? JsonObject).orEmpty()
    val changed = if (value is JsonNull) inner - key else inner + (key to value)
    return JsonObject(this + (property to JsonObject(changed)))
}

private val JsonObject.receivedMillis: Long?
    get() =
        (get("receivedAt") as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.let { Instant.parseOrNull(it.content) }
            ?.toEpochMilliseconds()

/** Row and member copies both show marks and mailboxes. */
private val PATCHED_KINDS = listOf(CacheKind.Email, CacheKind.Member)
