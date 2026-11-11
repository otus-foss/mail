package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.jmap.JmapConnection
import com.otusfoss.core.jmap.JmapRequestBuilder
import com.otusfoss.core.jmap.JmapResponse

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
    val limit = session().callBatch
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
