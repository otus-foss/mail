package com.otus.jmap

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The results of one request. Read each call's typed result with [get]. */
public class JmapResponse internal constructor(
    private val results: Map<String, List<JmapMethodResponse>>,
    /** The server's session state; when it differs from the session's, the session is outdated. */
    public val sessionState: String?,
) {
    /** The call's result, or [JmapException.MethodError] when the server rejected that call. */
    public operator fun <T> get(call: JmapCall<T>): T {
        val result = responses(call).first()
        if (result.name == ERROR) {
            throw JmapException.MethodError(
                result.arguments.string("type") ?: "unknown",
                result.arguments.string("description"),
            )
        }
        if (result.name != call.method.name) throw JmapException.NotJmap("unexpected method response")
        return jmapDecoding { call.method.decode(result.arguments) }
    }

    /** All responses, in wire order, including implicit calls and their errors (RFC 8620, section 3.2). */
    public fun responses(call: JmapCall<*>): List<JmapMethodResponse> =
        results[call.id] ?: throw JmapException.NotJmap("no response for ${call.method.name}")

    internal fun isOutdated(session: JmapSession): Boolean = sessionState != null && sessionState != session.state

    private companion object {
        const val ERROR = "error"
    }
}

/** One response emitted by an explicit or implicit method call. */
public data class JmapMethodResponse(
    val name: String,
    val arguments: JsonObject,
)

/** Validates the response envelope (RFC 8620, section 3.4) without trusting its shape. */
internal fun parseResponse(json: JsonObject): JmapResponse {
    val responses = json["methodResponses"] as? JsonArray ?: throw JmapException.NotJmap("missing methodResponses")
    val results = responses.map(::invocation).groupBy({ it.first }, { it.second })
    return JmapResponse(results, json.string("sessionState"))
}

/** One `[name, arguments, id]` triple (RFC 8620, section 3.2), checked element by element. */
private fun invocation(item: JsonElement): Pair<String, JmapMethodResponse> {
    val parts = (item as? JsonArray)?.takeIf { it.size == INVOCATION_SIZE }
    val name = parts?.get(0)?.stringContent()
    val arguments = parts?.get(1) as? JsonObject
    val id = parts?.get(2)?.stringContent()
    return if (name != null && arguments != null && id != null) {
        id to JmapMethodResponse(name, arguments)
    } else {
        throw JmapException.NotJmap("malformed method response")
    }
}

/**
 * Runs [read] over server data. Malformed or unexpected values, including unparseable dates, become
 * [JmapException.NotJmap], so server data can never crash the caller.
 */
public inline fun <T> jmapDecoding(read: () -> T): T =
    try {
        read()
    } catch (error: SerializationException) {
        throw JmapException.NotJmap("unreadable data", error)
    } catch (error: IllegalArgumentException) {
        throw JmapException.NotJmap("unexpected value", error)
    }

internal fun JsonObject.string(key: String): String? = get(key)?.stringContent()

private fun JsonElement.stringContent(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private const val INVOCATION_SIZE = 3
