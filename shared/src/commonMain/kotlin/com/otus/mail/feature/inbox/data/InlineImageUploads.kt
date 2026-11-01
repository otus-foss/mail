package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapUpload
import com.otus.mail.core.mail.MailAccount
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The blobs `data:` images were uploaded as, by account and URI, so a signature's images upload
 * once rather than at every save and send. An upload is reused for [KEEP], well within the hour a
 * server keeps a blob nothing refers to yet (RFC 8620, section 6.1), and only the [MAX_UPLOADS]
 * latest are kept. Only images up to [MAX_KEPT_BYTES], the size of signature images, are kept, since
 * the key holds the whole URI; a larger one, such as a pasted photo, uploads each time.
 */
internal class InlineImageUploads(
    private val time: TimeSource = TimeSource.Monotonic,
) {
    private val lock = Mutex()
    private val uploads = LinkedHashMap<String, Upload>()

    /** The blob for [image] in [account]: an earlier upload while it lasts, else a new one. */
    suspend fun upload(
        account: MailAccount,
        image: DataImage,
    ): JmapUpload =
        if (image.bytes.size > MAX_KEPT_BYTES) {
            account.connection.upload(account.id, image.bytes, image.type)
        } else {
            kept(account, image)
        }

    private suspend fun kept(
        account: MailAccount,
        image: DataImage,
    ): JmapUpload {
        val key = "${account.login.id}\n${account.id}\n${image.uri}"
        val earlier = lock.withLock { uploads[key]?.takeIf { it.at.elapsedNow() < KEEP } }
        if (earlier != null) return earlier.blob
        val blob = account.connection.upload(account.id, image.bytes, image.type)
        lock.withLock {
            uploads.remove(key)
            uploads[key] = Upload(blob, time.markNow())
            if (uploads.size > MAX_UPLOADS) uploads.remove(uploads.keys.first())
        }
        return blob
    }

    private class Upload(
        val blob: JmapUpload,
        val at: TimeMark,
    )

    private companion object {
        val KEEP = 30.minutes
        const val MAX_UPLOADS = 16
        const val MAX_KEPT_BYTES = 256 * 1024
    }
}
