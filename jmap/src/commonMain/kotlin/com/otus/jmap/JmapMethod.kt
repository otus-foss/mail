package com.otus.jmap

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One method a request can call (RFC 8620, section 3.2). [capability] is added to `using` and
 * checked against the session; [accountId], when set, is checked against that account.
 */
public abstract class JmapMethod<out T>(
    public val name: String,
    public val capability: String,
    public val accountId: String?,
) {
    public abstract fun arguments(): JsonObject

    /** Decodes this method's response arguments. Invalid data may throw; callers convert it. */
    public abstract fun decode(arguments: JsonObject): T
}

/** A data type such as `Mailbox`, `Email`, or `CalendarEvent`, with the serializer for its objects. */
public class JmapType<T>(
    public val name: String,
    public val capability: String,
    public val serializer: KSerializer<T>,
)

/** A call within a request. Use [resultOf] to feed its result into a later call (section 3.7). */
public class JmapCall<out T> internal constructor(
    public val method: JmapMethod<T>,
    internal val id: String,
) {
    public fun resultOf(path: String): JmapReference = JmapReference(id, method.name, path)
}

/** A result reference (RFC 8620, section 3.7). */
public class JmapReference internal constructor(
    private val resultOf: String,
    private val name: String,
    private val path: String,
) {
    internal fun toJson(): JsonObject =
        buildJsonObject {
            put("resultOf", JsonPrimitive(resultOf))
            put("name", JsonPrimitive(name))
            put("path", JsonPrimitive(path))
        }
}

/** Collects the calls of one request, in order. */
public class JmapRequestBuilder internal constructor() {
    internal val calls: MutableList<JmapCall<*>> = mutableListOf()

    public fun <T> call(method: JmapMethod<T>): JmapCall<T> = JmapCall(method, "c${calls.size}").also { calls += it }
}

/** Which objects a `/get` returns. */
public sealed class JmapIds {
    public data object All : JmapIds()

    public data class Of(
        val ids: List<String>,
    ) : JmapIds()

    public data class Result(
        val reference: JmapReference,
    ) : JmapIds()
}

internal fun strings(values: List<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })
