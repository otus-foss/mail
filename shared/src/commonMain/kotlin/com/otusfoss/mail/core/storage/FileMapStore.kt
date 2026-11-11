package com.otusfoss.mail.core.storage

import com.otusfoss.core.storage.StringMapStore
import com.otusfoss.mail.core.files.moveFile
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * A [StringMapStore] kept as one JSON [file], in a folder the platform has already prepared, such
 * as one left out of backups. Until the file exists, it reads [moved], the store it replaces, and
 * the first write empties that store, so its values move here once.
 */
class FileMapStore(
    private val file: Path,
    private val moved: StringMapStore,
) : StringMapStore {
    override fun read(): Map<String, String> =
        if (SystemFileSystem.exists(file)) {
            saved()
        } else {
            moved.read().also { values -> if (values.isNotEmpty()) write(values) }
        }

    override fun write(values: Map<String, String>) {
        val moving = !SystemFileSystem.exists(file)
        // A unique name per write, so two writes at once never share a half-written file.
        val partial = Path(checkNotNull(file.parent), "${file.name}.${Random.nextLong().toULong()}.partial")
        SystemFileSystem.sink(partial).buffered().use { it.writeString(Json.encodeToString(values)) }
        moveFile(partial, file)
        if (moving && moved.read().isNotEmpty()) moved.write(emptyMap())
    }

    /** The saved map; empty when the file no longer reads, so one bad write cannot stop the app. */
    private fun saved(): Map<String, String> =
        try {
            Json.decodeFromString(SystemFileSystem.source(file).buffered().use { it.readString() })
        } catch (_: SerializationException) {
            emptyMap()
        }
}
