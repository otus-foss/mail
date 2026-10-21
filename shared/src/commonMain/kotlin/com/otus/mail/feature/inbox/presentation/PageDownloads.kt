package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.AttachmentDownloads
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage

/**
 * The attachments a Read view downloaded, kept while it is open, so the bytes a thumbnail fetched
 * also serve Open and Save. The newest files are kept up to [maxBytes] together.
 */
class PageDownloads(
    private val downloads: AttachmentDownloads?,
    private val maxBytes: Long = MAX_KEPT_BYTES,
) {
    // Oldest first.
    private var kept = emptyList<Pair<String, ByteArray>>()

    /** [file]'s bytes, downloaded once; null when it cannot be had. */
    suspend fun bytes(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray? {
        val key = "${message.id}/${file.blobId}"
        return kept.firstOrNull { it.first == key }?.second
            ?: downloads?.download(message, file)?.also { keep(key, it) }
    }

    private fun keep(
        key: String,
        bytes: ByteArray,
    ) {
        val newestFirst = (kept + (key to bytes)).asReversed()
        val totals = newestFirst.runningFold(0L) { total, (_, data) -> total + data.size }.drop(1)
        kept =
            newestFirst
                .zip(totals)
                .takeWhile { it.second <= maxBytes }
                .map { it.first }
                .asReversed()
    }

    private companion object {
        const val MAX_KEPT_BYTES = 16L * 1024 * 1024
    }
}
