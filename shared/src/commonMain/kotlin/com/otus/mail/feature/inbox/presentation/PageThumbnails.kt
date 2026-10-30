package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage

/** The small pictures of the Read view's image attachments, kept per page while the view is open. */
@Stable
class PageThumbnails {
    private val images = mutableStateMapOf<String, ImageBitmap>()

    /** The picture of [file] in [message] once [load] has it; null for files without one. */
    fun of(
        message: MailMessage,
        file: MailAttachment,
    ): ImageBitmap? = images[key(message, file)]

    /**
     * Loads each of [message]'s [files] that has no picture yet with [fetch]. A load cut short, such
     * as by swiping away, or one that failed is tried again when the page shows again; files that
     * get no picture, such as PDFs, cost nothing to ask again.
     */
    suspend fun load(
        message: MailMessage,
        files: List<MailAttachment>,
        fetch: suspend (MailAttachment) -> ImageBitmap?,
    ) {
        files
            .filterNot { key(message, it) in images }
            .forEach { file ->
                val image = fetch(file)
                if (image != null) images[key(message, file)] = image
            }
    }

    private fun key(
        message: MailMessage,
        file: MailAttachment,
    ) = "${message.id}/${file.blobId}"
}
