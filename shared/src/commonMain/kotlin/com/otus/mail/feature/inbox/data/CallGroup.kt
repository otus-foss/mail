package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnection
import com.otus.jmap.JmapRequestBuilder
import com.otus.jmap.JmapResponse
import com.otus.jmap.JmapSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Method calls that must share one request, such as a `/get` and the calls that refer to its
 * result (RFC 8620, section 3.7). [add] adds [size] calls and returns how to read their result.
 */
internal class CallGroup<out T>(
    val size: Int,
    val add: JmapRequestBuilder.() -> (JmapResponse) -> T,
)

/**
 * Sends [groups] in as few requests as the server's `maxCallsInRequest` allows, one after the
 * other, and returns their results in order. A group larger than the limit goes alone.
 */
internal suspend fun <T> JmapConnection.sendAll(groups: List<CallGroup<T>>): List<T> {
    val limit = session().coreLimit("maxCallsInRequest", SUGGESTED_MAX_CALLS)
    val batches =
        groups.fold(emptyList<List<CallGroup<T>>>()) { batches, group ->
            val last = batches.lastOrNull()
            if (last != null && last.sumOf { it.size } + group.size <= limit) {
                batches.dropLast(1) + listOf(last + group)
            } else {
                batches + listOf(listOf(group))
            }
        }
    return batches.flatMap { batch ->
        lateinit var readers: List<(JmapResponse) -> T>
        val response = send { readers = batch.map { it.add(this) } }
        readers.map { it(response) }
    }
}

/**
 * The core capability's limit [name] (RFC 8620, section 2), such as `maxObjectsInGet`, or
 * [suggested], the minimum the RFC suggests, when the server does not say.
 */
internal fun JmapSession.coreLimit(
    name: String,
    suggested: Int,
): Int =
    (capabilityObjects[JmapCapability.CORE]?.get(name) as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.longOrNull
        ?.takeIf { it > 0 }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
        ?: suggested

private const val SUGGESTED_MAX_CALLS = 16
