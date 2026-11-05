package com.otus.mail.core.files

/**
 * Where a stored file lives: [group] and [id] are safe as folder names, such as hex digests. A
 * group holds one login's files, so removing the login drops them together.
 */
data class StoredKey(
    val group: String,
    val id: String,
)

/**
 * A complete file in the folder. [path] is the platform's path to its bytes, which the platform's
 * file handling can open or copy. [usedAtMillis] is when it was last written or found. [kept]
 * files stay until the user removes them; the others may be trimmed.
 */
data class StoredEntry(
    val key: StoredKey,
    val path: String,
    val bytes: Long,
    val usedAtMillis: Long,
    val kept: Boolean,
)

/** Thrown when the device cannot store or read a file, such as a full disk. */
class DownloadFolderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The app's private folder for downloaded files, which neither backups nor device transfer copy.
 * Platform source sets implement it; see docs/storage.md. Failures throw [DownloadFolderException];
 * cancellation propagates.
 */
interface DownloadFolder {
    /** The complete file under [key], marked as used now; null when there is none. */
    suspend fun find(key: StoredKey): StoredEntry?

    /**
     * Writes a file named [name] under [key]: [fill] receives each piece in order. Until [fill]
     * returns, the bytes go to a partial file that [find] does not see, so a failed or cancelled
     * write leaves nothing behind and keeps any file that was there. Replacing keeps its kept mark.
     */
    suspend fun write(
        key: StoredKey,
        name: String,
        fill: suspend (append: suspend (ByteArray) -> Unit) -> Unit,
    ): StoredEntry

    /** The bytes of the file under [key]; null when there is none. Only for small files. */
    suspend fun read(key: StoredKey): ByteArray?

    /** Marks the file under [key] kept or not; false when there is none. */
    suspend fun setKept(
        key: StoredKey,
        kept: Boolean,
    ): Boolean

    /** Every complete file, in no order. */
    suspend fun entries(): List<StoredEntry>

    suspend fun delete(key: StoredKey)

    /** Deletes every file of [group], kept or not. */
    suspend fun deleteGroup(group: String)

    /** Deletes partial files left by a write that the process did not finish. */
    suspend fun dropUnfinished()
}
