package com.otus.mail.core.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** How a stored key or value becomes text and back. [decode] gives null for text it cannot read. */
class TextCodec<T>(
    val encode: (T) -> String,
    val decode: (String) -> T?,
) {
    /** This codec for values that become a [T] through [from] and come back through [to]. */
    fun <R> map(
        to: (T) -> R,
        from: (R) -> T,
    ): TextCodec<R> = TextCodec({ encode(from(it)) }, { text -> decode(text)?.let(to) })

    companion object {
        val Text = TextCodec<String>({ it }, { it })

        /** Text that reads as missing when it is blank. */
        val NonBlank = TextCodec<String>({ it }, { it.takeIf(String::isNotBlank) })

        /** One item per line, for items that never hold a line break; blank lines read as none. */
        val Lines = TextCodec<List<String>>({ it.joinToString("\n") }, { it.lines().filter(String::isNotBlank) })

        /** [serializer]'s JSON in [json]; text that no longer reads, as after a format change, is missing. */
        fun <T> json(
            serializer: KSerializer<T>,
            json: Json,
        ): TextCodec<T> =
            TextCodec(
                { json.encodeToString(serializer, it) },
                { text -> decodedOrNull(json, serializer, text) },
            )
    }
}

private fun <T> decodedOrNull(
    json: Json,
    serializer: KSerializer<T>,
    text: String,
): T? =
    try {
        json.decodeFromString(serializer, text)
    } catch (expected: IllegalArgumentException) {
        // Not JSON, or JSON that no longer fits the type; SerializationException is one of these.
        null
    }
