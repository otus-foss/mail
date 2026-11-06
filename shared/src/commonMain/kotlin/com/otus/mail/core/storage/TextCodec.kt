package com.otus.mail.core.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** How a stored key or value becomes text and back. [decode] gives null for text it cannot read. */
class TextCodec<T>(
    val encode: (T) -> String,
    val decode: (String) -> T?,
) {
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
                { text -> runCatching { json.decodeFromString(serializer, text) }.getOrNull() },
            )
    }
}
