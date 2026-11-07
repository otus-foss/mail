package com.otus.mail.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Downloaded files in the folder at [root], which each platform keeps out of backups; see
 * docs/storage.md. Each file has a folder `group/id/` holding the file under its own name, so the
 * app that opens it sees that name, plus a `.kept` mark when the user keeps it and a `.partial`
 * file while it downloads. The file's modification time records its last use.
 */
class FileDownloadFolder(
    root: String,
) : DownloadFolder {
    private val root = Path(root)

    override suspend fun find(key: StoredKey): StoredEntry? =
        disk("find") {
            val file = contentOf(folderOf(key)) ?: return@disk null
            markUsed(file)
            entryOf(key, file)
        }

    override suspend fun write(
        key: StoredKey,
        name: String,
        fill: suspend (append: suspend (ByteArray) -> Unit) -> Unit,
    ): StoredEntry =
        disk("write") {
            prepareDownloadRoot(root)
            val folder = folderOf(key)
            val partial = Path(folder, PARTIAL)
            try {
                SystemFileSystem.createDirectories(folder)
                SystemFileSystem.sink(partial).buffered().use { output -> fill { piece -> output.append(piece) } }
                contentOf(folder)?.let { SystemFileSystem.delete(it) }
                val file = Path(folder, safeName(name))
                moveFile(partial, file)
                entryOf(key, file)
            } finally {
                SystemFileSystem.delete(partial, mustExist = false)
            }
        }

    override suspend fun read(key: StoredKey): ByteArray? =
        disk("read") {
            val file = contentOf(folderOf(key)) ?: return@disk null
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        }

    override suspend fun setKept(
        key: StoredKey,
        kept: Boolean,
    ): Boolean =
        disk("mark") {
            val folder = folderOf(key)
            if (contentOf(folder) == null) return@disk false
            val mark = Path(folder, KEPT)
            if (kept) SystemFileSystem.sink(mark).close() else SystemFileSystem.delete(mark, mustExist = false)
            true
        }

    override suspend fun entries(): List<StoredEntry> =
        disk("list") {
            childFolders(root)
                .flatMap { group -> childFolders(group).map { StoredKey(group.name, it.name) to it } }
                .mapNotNull { (key, folder) -> contentOf(folder)?.let { entryOf(key, it) } }
        }

    override suspend fun delete(key: StoredKey) {
        disk("delete") { deleteAll(folderOf(key)) }
    }

    override suspend fun deleteGroup(group: String) {
        disk("delete") { deleteAll(Path(root, group)) }
    }

    override suspend fun dropUnfinished() {
        disk("clean") {
            childFolders(root)
                .flatMap(::childFolders)
                .forEach { folder ->
                    SystemFileSystem.delete(Path(folder, PARTIAL), mustExist = false)
                    if (contentOf(folder) == null) deleteAll(folder)
                }
        }
    }

    private fun folderOf(key: StoredKey) = Path(root, key.group, key.id)

    private fun entryOf(
        key: StoredKey,
        file: Path,
    ) = StoredEntry(
        key,
        file.toString(),
        SystemFileSystem.metadataOrNull(file)?.size ?: 0L,
        modifiedAtMillis(file),
        SystemFileSystem.exists(Path(file.parent ?: root, KEPT)),
    )

    private companion object {
        const val PARTIAL = ".partial"
        const val KEPT = ".kept"
    }
}

/** The finished file in [folder]: the one whose name does not start with a dot. */
private fun contentOf(folder: Path): Path? =
    if (!SystemFileSystem.exists(folder)) {
        null
    } else {
        SystemFileSystem
            .list(folder)
            .firstOrNull { !it.name.startsWith(".") && SystemFileSystem.metadataOrNull(it)?.isRegularFile == true }
    }

private fun childFolders(folder: Path): List<Path> =
    if (!SystemFileSystem.exists(folder)) {
        emptyList()
    } else {
        SystemFileSystem.list(folder).filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == true }
    }

/** Deletes [path] and everything in it; nothing to delete counts as done. */
private fun deleteAll(path: Path) {
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) SystemFileSystem.list(path).forEach(::deleteAll)
    SystemFileSystem.delete(path, mustExist = false)
}

/**
 * Writes [piece], reporting a full or failing disk as [DownloadFolderException]. A plain
 * [IOException] here would read as a network failure to the JMAP client that calls back.
 */
private fun Sink.append(piece: ByteArray) {
    try {
        write(piece)
    } catch (error: IOException) {
        throw DownloadFolderException("Cannot write a downloaded file", error)
    }
}

/**
 * The sender picks the name, so path separators cannot leave the folder, and a leading dot, which
 * marks the folder's own files, is dropped.
 */
private fun safeName(name: String): String =
    name
        .replace('/', '_')
        .replace('\\', '_')
        .trim()
        .trimStart('.')
        .ifEmpty { "attachment" }

/** Runs [block] on the IO dispatcher, reporting disk failures as [DownloadFolderException]. */
private suspend fun <T> disk(
    action: String,
    block: suspend () -> T,
): T =
    withContext(Dispatchers.IO) {
        reportingDiskFailures({ DownloadFolderException("Cannot $action a downloaded file", it) }, block)
    }
