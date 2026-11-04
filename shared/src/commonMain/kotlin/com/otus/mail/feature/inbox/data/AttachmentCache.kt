package com.otus.mail.feature.inbox.data

import com.otus.mail.core.files.DownloadFolder
import com.otus.mail.core.files.DownloadFolderException
import com.otus.mail.core.files.StoredEntry
import com.otus.mail.core.files.StoredKey
import com.otus.mail.core.files.storedName
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OfflineAttachments
import com.otus.mail.feature.inbox.domain.StoredFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Attachments streamed from the server into [folder], one file per login, blob, and name. After
 * each download, [retention] makes room among the files the user does not keep. See
 * docs/storage.md.
 */
class AttachmentCache(
    private val stream: AttachmentStream,
    private val folder: DownloadFolder,
    private val retention: AttachmentRetention = AttachmentRetention(folder),
) : OfflineAttachments {
    // One lock per file, so a thumbnail and an Open of one file download it once. A few bytes per
    // file opened while the process runs.
    private val locks = Mutex()
    private val fileLocks = mutableMapOf<StoredKey, Mutex>()

    override suspend fun fetch(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredFile? = entry(message, file, progress)?.let { file.stored(it) }

    override suspend fun bytes(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray? {
        val entry = entry(message, file) {} ?: return null
        return onDevice { folder.read(entry.key) }
    }

    override suspend fun isKept(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = onDevice { folder.find(keyOf(message, file)) }?.kept ?: false

    override suspend fun keep(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredFile? {
        val entry = entry(message, file, progress) ?: return null
        val marked = onDevice { folder.setKept(entry.key, kept = true) } ?: false
        return if (marked) file.stored(entry.copy(kept = true)) else null
    }

    override suspend fun remove(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = onDevice { folder.delete(keyOf(message, file)) } != null

    private suspend fun entry(
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredEntry? {
        val key = keyOf(message, file)
        return lockOf(key).withLock { onDevice { folder.find(key) } ?: download(key, message, file, progress) }
    }

    private suspend fun download(
        key: StoredKey,
        message: MailMessage,
        file: MailAttachment,
        progress: (Long) -> Unit,
    ): StoredEntry? {
        val written =
            try {
                folder.write(key, file.name) { append -> receive(message, file, append, progress) }
            } catch (expected: DownloadFolderException) {
                null
            } catch (expected: TransferFailed) {
                null
            }
        if (written != null) onDevice { retention.fitBudget(newest = key) }
        return written
    }

    /**
     * Streams [file] into [append]. A server that sends more than the size the message gives,
     * which is the decoded size (RFC 8621, section 4.1.4), is stopped, so it cannot fill the disk.
     */
    private suspend fun receive(
        message: MailMessage,
        file: MailAttachment,
        append: suspend (ByteArray) -> Unit,
        progress: (Long) -> Unit,
    ) {
        var received = 0L
        val reached =
            stream.stream(message, file) { piece ->
                received += piece.size
                if (file.size > 0 && received > file.size) throw TransferFailed()
                append(piece)
                progress(received)
            }
        if (!reached) throw TransferFailed()
    }

    private suspend fun lockOf(key: StoredKey): Mutex = locks.withLock { fileLocks.getOrPut(key) { Mutex() } }

    /** Thrown inside a write to discard it, when the server failed or sent too much. */
    private class TransferFailed : Exception()
}

/** Where [file] of [message] lives: a folder per login, and one per blob and name in it. */
private fun keyOf(
    message: MailMessage,
    file: MailAttachment,
) = StoredKey(storedName(message.location.accountId), storedName("${file.blobId}/${file.name}"))

private fun MailAttachment.stored(entry: StoredEntry) = StoredFile(entry.path, name, type, entry.bytes, entry.kept)

/** [block]'s result; null when the device failed to store or read. */
private inline fun <T> onDevice(block: () -> T): T? =
    try {
        block()
    } catch (expected: DownloadFolderException) {
        null
    }
