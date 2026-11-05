package com.otus.mail

import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.DownloadFolderException
import com.otus.mail.core.files.StoredEntry
import com.otus.mail.core.files.StoredKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Downloaded files in [root], inside the app's private files folder, which the backup and data
 * extraction rules leave out. Each file has a folder `group/id/` holding the file under its own
 * name, so the app that opens it sees that name, plus a `.kept` mark when the user keeps it and a
 * `.partial` file while it downloads. The file's modification time records its last use.
 */
class AndroidDownloadFolder(
    private val root: File,
) : DownloadFolder {
    override suspend fun find(key: StoredKey): StoredEntry? =
        disk("find") {
            val file = contentOf(folderOf(key)) ?: return@disk null
            file.setLastModified(System.currentTimeMillis())
            entryOf(key, file)
        }

    override suspend fun write(
        key: StoredKey,
        name: String,
        fill: suspend (append: suspend (ByteArray) -> Unit) -> Unit,
    ): StoredEntry =
        disk("write") {
            val folder = folderOf(key)
            val partial = File(folder, PARTIAL)
            try {
                if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create $folder")
                FileOutputStream(partial).use { output -> fill { piece -> output.append(piece) } }
                val replaced = contentOf(folder)
                if (replaced != null && !replaced.delete()) throw IOException("Cannot replace $replaced")
                val file = File(folder, safeName(name))
                if (!partial.renameTo(file)) throw IOException("Cannot finish $file")
                entryOf(key, file)
            } finally {
                partial.delete()
            }
        }

    override suspend fun read(key: StoredKey): ByteArray? = disk("read") { contentOf(folderOf(key))?.readBytes() }

    override suspend fun setKept(
        key: StoredKey,
        kept: Boolean,
    ): Boolean =
        disk("mark") {
            val folder = folderOf(key)
            if (contentOf(folder) == null) return@disk false
            val mark = File(folder, KEPT)
            if (kept) mark.createNewFile() else mark.delete()
            true
        }

    override suspend fun entries(): List<StoredEntry> =
        disk("list") {
            root
                .childFolders()
                .flatMap { group -> group.childFolders().map { StoredKey(group.name, it.name) to it } }
                .mapNotNull { (key, folder) -> contentOf(folder)?.let { entryOf(key, it) } }
        }

    override suspend fun delete(key: StoredKey) {
        disk("delete") { folderOf(key).deleteAll() }
    }

    override suspend fun deleteGroup(group: String) {
        disk("delete") { File(root, group).deleteAll() }
    }

    override suspend fun dropUnfinished() {
        disk("clean") {
            root
                .childFolders()
                .flatMap { it.childFolders() }
                .forEach { folder ->
                    File(folder, PARTIAL).delete()
                    if (contentOf(folder) == null) folder.deleteRecursively()
                }
        }
    }

    private fun folderOf(key: StoredKey) = File(File(root, key.group), key.id)

    private fun entryOf(
        key: StoredKey,
        file: File,
    ) = StoredEntry(key, file.absolutePath, file.length(), file.lastModified(), File(file.parentFile, KEPT).exists())

    private companion object {
        const val PARTIAL = ".partial"
        const val KEPT = ".kept"
    }
}

/** The finished file in [folder]: the one whose name does not start with a dot. */
private fun contentOf(folder: File): File? =
    folder
        .listFiles()
        .orEmpty()
        .firstOrNull { it.isFile && !it.name.startsWith(".") }

/** Deletes this file or folder and everything in it; nothing to delete counts as done. */
private fun File.deleteAll() {
    if (!deleteRecursively()) throw IOException("Cannot delete $this")
}

private fun File.childFolders(): List<File> = listFiles().orEmpty().filter { it.isDirectory }

/**
 * Writes [piece], reporting a full or failing disk as [DownloadFolderException]. A plain
 * [IOException] here would read as a network failure to the JMAP client that calls back.
 */
private fun OutputStream.append(piece: ByteArray) {
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
        } catch (error: SecurityException) {
            throw DownloadFolderException("Cannot $action a downloaded file", error)
        }
    }
