package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.ui.graphics.ImageBitmap
import com.otus.designsystem.mail.attachments.components.AttachmentActions
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.elements.ToastAction
import com.otus.designsystem.shared.people.elements.decodeImage
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import com.otus.mail.feature.inbox.presentation.NewsletterStep
import com.otus.mail.feature.inbox.presentation.PageDownloads
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.read.AttachmentKindName
import com.otus.mail.feature.inbox.presentation.read.kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the strips below a page do with its files and its mailing list. Downloads and requests run
 * in the process's scope, so they finish after the page closes, and say how they went in a toast.
 * A file is downloaded once while the view is open, for its thumbnail, Open, and Save alike.
 * [openLink] opens a web page outside the app.
 */
internal class ReadStripActions(
    private val read: ReadContext,
    private val openLink: (String) -> Unit,
) {
    private val toasts get() = read.effects.toasts
    private val newsletters get() = read.effects.newsletters
    private val downloads = PageDownloads(read.files.downloads)

    /** Opening, saving, and saving every one of [files] of [message]. */
    fun files(
        message: MailMessage,
        files: List<MailAttachment>,
    ): AttachmentActions =
        AttachmentActions(
            onOpen = { index -> open(message, files[index]) },
            onSave = { index -> save(message, listOf(files[index])) },
            onSaveAll = { save(message, files) },
        )

    /**
     * A small picture of [file] for the strip, when it is an image small enough to download just
     * for that; null for other files or when it cannot be had.
     */
    suspend fun thumbnail(
        message: MailMessage,
        file: MailAttachment,
    ): ImageBitmap? =
        file
            .takeIf { it.kind() == AttachmentKindName.Image && it.size <= THUMBNAIL_MAX_BYTES }
            ?.let { downloads.bytes(message, it) }
            ?.let { withContext(Dispatchers.Default) { decodeImage(it, THUMBNAIL_PIXELS) } }

    private fun open(
        message: MailMessage,
        file: MailAttachment,
    ) {
        val saved = read.files.saved ?: return
        read.effects.launch {
            val bytes = downloads.bytes(message, file)
            if (bytes == null || !saved.open(file.name, file.type, bytes)) {
                toasts.show(ToastMessage("Couldn't open ${file.name}"))
            }
        }
    }

    private fun save(
        message: MailMessage,
        files: List<MailAttachment>,
    ) {
        val saved = read.files.saved ?: return
        // A download can take a while, so the tap answers at once and the outcome replaces it.
        val group = Any()
        val saving = if (files.size == 1) "Saving ${files.single().name}…" else "Saving ${files.size} files…"
        toasts.show(ToastMessage(saving, group = group, sticky = true))
        read.effects.launch {
            val failed =
                files.filterNot { file ->
                    val bytes = downloads.bytes(message, file)
                    bytes != null && saved.save(file.name, file.type, bytes)
                }
            val text =
                when {
                    failed.isEmpty() -> "Saved to Downloads"
                    failed.size == 1 -> "Couldn't save ${failed.single().name}"
                    else -> "Couldn't save ${failed.size} files"
                }
            toasts.show(ToastMessage(text, group = group))
        }
    }

    /** Where [message]'s newsletter strip stands. */
    fun step(message: MailMessage): NewsletterStep = newsletters.of(message)

    /** The mailing list [message] came from; null when it is from none or the server failed. */
    suspend fun list(message: MailMessage): MailingList? = read.services.newsletters?.list(message)

    /**
     * Unsubscribes from [message]'s list. A list that takes a request shows the strip's after-state
     * at once and sends the request only when the Undo toast closes, since a sent request cannot be
     * taken back. A list that only has a web page opens it.
     */
    fun unsubscribe(
        message: MailMessage,
        how: Unsubscribe,
        title: String,
    ) {
        val lists = read.services.newsletters ?: return
        val web = how.web
        if (how.mail == null && web != null) return openLink(web)
        newsletters.set(message, NewsletterStep.Unsubscribed)
        val undo = ToastAction("Undo") { newsletters.set(message, NewsletterStep.Subscribed) }
        val send = {
            read.effects.launch {
                when (val result = lists.unsubscribe(message, how)) {
                    UnsubscribeResult.Done -> Unit
                    is UnsubscribeResult.OpenLink -> openLink(result.url)
                    UnsubscribeResult.Failed -> failed(message, title)
                }
            }
        }
        toasts.show(ToastMessage("Unsubscribed from $title", undo, onClose = send))
    }

    private fun failed(
        message: MailMessage,
        title: String,
    ) {
        newsletters.set(message, NewsletterStep.Subscribed)
        toasts.show(ToastMessage("Couldn't unsubscribe from $title. Try again."))
    }

    /** Archives the [list]'s older mail, after unsubscribing. */
    fun archiveOlder(
        message: MailMessage,
        list: MailingList,
    ) {
        val lists = read.services.newsletters ?: return
        newsletters.set(message, NewsletterStep.Archived)
        read.effects.launch {
            val done = lists.archive(message.location.accountId, list.olderIds)
            if (!done) newsletters.set(message, NewsletterStep.Unsubscribed)
            val count = list.olderIds.size
            toasts.show(
                ToastMessage(if (done) "$count older digests archived" else "Couldn't archive them. Try again."),
            )
        }
    }
}

/** The largest image the strip downloads only to show its picture. */
private const val THUMBNAIL_MAX_BYTES = 2L * 1024 * 1024

/** Enough pixels for a 40 dp tile on a dense screen. */
private const val THUMBNAIL_PIXELS = 160
