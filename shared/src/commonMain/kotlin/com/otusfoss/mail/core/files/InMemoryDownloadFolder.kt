package com.otusfoss.mail.core.files

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [DownloadFolder] in memory, for tests, previews, and platforms without one. [now] gives the
 * time in milliseconds that marks a file as used.
 */
class InMemoryDownloadFolder(
    private val now: () -> Long = { 0L },
) : DownloadFolder {
    private class Held(
        val name: String,
        val bytes: ByteArray,
        val usedAt: Long,
        val kept: Boolean,
    )

    private val lock = Mutex()
    private val files = mutableMapOf<StoredKey, Held>()

    override suspend fun find(key: StoredKey): StoredEntry? =
        lock.withLock {
            val file = files[key] ?: return@withLock null
            val used = Held(file.name, file.bytes, now(), file.kept)
            files[key] = used
            used.entry(key)
        }

    override suspend fun write(
        key: StoredKey,
        name: String,
        fill: suspend (append: suspend (ByteArray) -> Unit) -> Unit,
    ): StoredEntry {
        val pieces = mutableListOf<ByteArray>()
        fill { pieces += it }
        val bytes = pieces.fold(ByteArray(0), ByteArray::plus)
        return lock.withLock {
            val written = Held(name, bytes, now(), files[key]?.kept ?: false)
            files[key] = written
            written.entry(key)
        }
    }

    override suspend fun read(key: StoredKey): ByteArray? = lock.withLock { files[key]?.bytes }

    override suspend fun setKept(
        key: StoredKey,
        kept: Boolean,
    ): Boolean =
        lock.withLock {
            val file = files[key] ?: return@withLock false
            files[key] = Held(file.name, file.bytes, file.usedAt, kept)
            true
        }

    override suspend fun entries(): List<StoredEntry> = lock.withLock { files.map { (key, file) -> file.entry(key) } }

    override suspend fun delete(key: StoredKey) {
        lock.withLock { files.remove(key) }
    }

    override suspend fun deleteGroup(group: String) {
        lock.withLock { files.keys.removeAll { it.group == group } }
    }

    override suspend fun dropUnfinished() = Unit

    private fun Held.entry(key: StoredKey) =
        StoredEntry(key, "memory:${key.group}/${key.id}/$name", bytes.size.toLong(), usedAt, kept)
}
