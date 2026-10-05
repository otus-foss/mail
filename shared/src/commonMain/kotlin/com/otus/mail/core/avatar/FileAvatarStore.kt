package com.otus.mail.core.avatar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** App-private, bounded encoded-image cache. Records use hashed keys and atomic replacement. */
class FileAvatarStore(
    directory: String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntries: Int = 256,
) : AvatarStore {
    private val directory = Path(directory)
    private val lock = Mutex()
    private val files = SystemFileSystem

    init {
        require(maxBytes >= HEADER_BYTES && maxEntries > 0)
    }

    override suspend fun read(key: AvatarCacheKey): StoredAvatar? =
        access {
            val path = path(key)
            val size = files.metadataOrNull(path)?.size ?: return@access null
            if (size !in HEADER_BYTES..MAX_AVATAR_BYTES + HEADER_BYTES) {
                files.delete(path)
                return@access null
            }
            files.source(path).buffered().use { source ->
                val created = source.readLong()
                val kind = source.readByte().toInt()
                val bytes = source.readByteArray()
                when {
                    created < 0 -> null
                    kind == 0 && bytes.isEmpty() -> StoredAvatar(null, created)
                    kind == 1 && bytes.isNotEmpty() -> StoredAvatar(AvatarImage(bytes), created)
                    else -> null
                }
            }
        }

    override suspend fun write(
        key: AvatarCacheKey,
        entry: StoredAvatar,
    ) {
        access {
            val bytes = entry.image?.bytes
            if (bytes != null && (bytes.isEmpty() || bytes.size > MAX_AVATAR_BYTES)) return@access
            files.createDirectories(directory)
            val target = path(key)
            val temporary = Path(directory, "${target.name}.part")
            try {
                files.sink(temporary).buffered().use { sink ->
                    sink.writeLong(entry.created)
                    sink.writeByte(if (bytes == null) 0 else 1)
                    if (bytes != null) sink.write(bytes)
                }
                files.atomicMove(temporary, target)
                prune()
            } finally {
                files.delete(temporary, mustExist = false)
            }
        }
    }

    override suspend fun clear() {
        access { records().forEach { files.delete(it) } }
    }

    override suspend fun removeAccount(account: String) {
        access {
            val prefix = "${avatarCacheDigest(account)}-"
            records().filter { it.name.startsWith(prefix) }.forEach { files.delete(it) }
        }
    }

    private fun path(key: AvatarCacheKey): Path {
        val partition = key.account?.let(::avatarCacheDigest) ?: "public"
        return Path(directory, "$partition-${avatarCacheDigest(key.value)}.avatar")
    }

    private fun records(): List<Path> =
        if (files.exists(directory)) {
            files.list(directory).filter { it.name.endsWith(".avatar") || it.name.endsWith(".avatar.part") }
        } else {
            emptyList()
        }

    private fun prune() {
        // A process killed during a write can leave a temporary record behind.
        records().filter { it.name.endsWith(".part") }.forEach { files.delete(it) }
        val records =
            records()
                .map { path ->
                    val size = files.metadataOrNull(path)?.size ?: 0
                    val created = runCatching { files.source(path).buffered().use { it.readLong() } }.getOrDefault(0)
                    DiskRecord(path, size, created)
                }.sortedBy { it.created }
        var bytes = records.sumOf { it.size }
        var count = records.size
        records.forEach { record ->
            if (bytes > maxBytes || count > maxEntries) {
                files.delete(record.path)
                bytes -= record.size
                count--
            }
        }
    }

    private suspend fun <T> access(block: () -> T): T? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching(block).getOrElse { error ->
                    if (error is CancellationException) throw error
                    // Optional cache IO failures must never hide the inbox or fail sign-out.
                    null
                }
            }
        }

    private class DiskRecord(
        val path: Path,
        val size: Long,
        val created: Long,
    )

    private companion object {
        const val HEADER_BYTES = 9L
        const val DEFAULT_MAX_BYTES = 32L * 1024 * 1024
    }
}

internal expect fun avatarCacheDigest(value: String): String
