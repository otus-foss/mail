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
    private val tried = mutableSetOf<String>()

    /** The picture of [file] in [message] once [load] has it; null for files without one. */
    fun of(
        message: MailMessage,
        file: MailAttachment,
    ): ImageBitmap? = images[key(message, file)]

    /**
     * Loads each of [message]'s [files] not tried yet with [fetch], once per page. A load cut short,
     * such as by swiping away, is tried again when the page shows again.
     */
    suspend fun load(
        message: MailMessage,
        files: List<MailAttachment>,
        fetch: suspend (MailAttachment) -> ImageBitmap?,
    ) {
        files
            .filterNot { key(message, it) in tried }
            .forEach { file ->
                val image = fetch(file)
                val key = key(message, file)
                tried += key
                if (image != null) images[key] = image
            }
    }

    private fun key(
        message: MailMessage,
        file: MailAttachment,
    ) = "${message.id}/${file.blobId}"
}
