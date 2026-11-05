package com.otus.mail

import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.DownloadFolderException
import com.otus.mail.core.files.StoredEntry
import com.otus.mail.core.files.StoredKey
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.timeIntervalSince1970

/**
 * Downloaded files in [root], in Application Support, which iOS does not empty, marked to stay out
 * of backups and readable only after the first unlock. Each file has a folder `group/id/` holding
 * the file under its own name, plus a `.kept` mark when the user keeps it and a `.partial` file
 * while it downloads. The file's modification date records its last use.
 */
class IosDownloadFolder(
    private val root: String,
) : DownloadFolder {
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
            prepareRoot(root)
            val folder = folderOf(key)
            val partial = Path(folder, PARTIAL)
            try {
                SystemFileSystem.createDirectories(folder)
                SystemFileSystem.sink(partial).buffered().use { output -> fill { piece -> output.append(piece) } }
                val replaced = contentOf(folder)
                if (replaced != null) SystemFileSystem.delete(replaced)
                val file = Path(folder, safeName(name))
                SystemFileSystem.atomicMove(partial, file)
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
            childFolders(Path(root))
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
            childFolders(Path(root))
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
    ): StoredEntry {
        val size = SystemFileSystem.metadataOrNull(file)?.size ?: 0L
        val kept = SystemFileSystem.exists(Path(file.parent ?: Path(root), KEPT))
        return StoredEntry(key, file.toString(), size, usedAtMillis(file), kept)
    }

    private companion object {
        const val PARTIAL = ".partial"
        const val KEPT = ".kept"
    }
}

/** Creates [root] with its protection class, which files made inside take, and keeps it out of backups. */
@OptIn(ExperimentalForeignApi::class)
private fun prepareRoot(root: String) {
    if (SystemFileSystem.exists(Path(root))) return
    val protection = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)
    if (!NSFileManager.defaultManager.createDirectoryAtPath(root, true, protection, null)) {
        throw IOException("Cannot create $root")
    }
    NSURL.fileURLWithPath(root).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
}

/** When [file] was last written or found, from its modification date. */
@OptIn(ExperimentalForeignApi::class)
private fun usedAtMillis(file: Path): Long {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(file.toString(), null)
    val modified = attributes?.get(NSFileModificationDate) as? NSDate
    return ((modified?.timeIntervalSince1970 ?: 0.0) * MILLIS_PER_SECOND).toLong()
}

/** Sets [file]'s modification date to now, which records it as used. */
@OptIn(ExperimentalForeignApi::class)
private fun markUsed(file: Path) {
    val now = mapOf<Any?, Any?>(NSFileModificationDate to NSDate())
    NSFileManager.defaultManager.setAttributes(now, file.toString(), null)
}

/** Deletes [path] and everything in it; nothing to delete counts as done. */
@OptIn(ExperimentalForeignApi::class)
private fun deleteAll(path: Path) {
    if (!SystemFileSystem.exists(path)) return
    if (!NSFileManager.defaultManager.removeItemAtPath(path.toString(), null)) throw IOException("Cannot delete $path")
}

private const val MILLIS_PER_SECOND = 1000.0

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
        try {
            block()
        } catch (error: IOException) {
            throw DownloadFolderException("Cannot $action a downloaded file", error)
        }
    }
