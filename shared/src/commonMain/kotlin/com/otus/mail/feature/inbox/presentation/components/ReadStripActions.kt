package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.components.AttachmentActions
import com.otus.designsystem.components.ToastMessage
import com.otus.designsystem.elements.ToastAction
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailingList
import com.otus.mail.feature.inbox.domain.Unsubscribe
import com.otus.mail.feature.inbox.domain.UnsubscribeResult
import com.otus.mail.feature.inbox.presentation.NewsletterStep
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadState

/**
 * What the strips below a page do with its files and its mailing list. Downloads and requests run
 * in the app's scope, so they finish after the page closes, and say how they went in a toast.
 * [openLink] opens a web page outside the app.
 */
internal class ReadStripActions(
    private val read: ReadContext,
    private val state: ReadState,
    private val openLink: (String) -> Unit,
) {
    private val toasts get() = read.effects.toasts

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

    private fun open(
        message: MailMessage,
        file: MailAttachment,
    ) {
        val saved = read.files.saved ?: return
        read.effects.launch {
            val bytes = read.files.downloads?.download(message, file)
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
        read.effects.launch {
            val failed =
                files.filterNot { file ->
                    val bytes = read.files.downloads?.download(message, file)
                    bytes != null && saved.save(file.name, file.type, bytes)
                }
            val text =
                when {
                    failed.isEmpty() -> "Saved to Downloads"
                    failed.size == 1 -> "Couldn't save ${failed.single().name}"
                    else -> "Couldn't save ${failed.size} files"
                }
            toasts.show(ToastMessage(text))
        }
    }

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
        val newsletters = read.services.newsletters ?: return
        val web = how.web
        if (how.oneClick == null && how.mail == null && web != null) return openLink(web)
        state.setNewsletter(message, NewsletterStep.Unsubscribed)
        val undo = ToastAction("Undo") { state.setNewsletter(message, NewsletterStep.Subscribed) }
        val send = {
            read.effects.launch {
                when (val result = newsletters.unsubscribe(message, how)) {
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
        state.setNewsletter(message, NewsletterStep.Subscribed)
        toasts.show(ToastMessage("Couldn't unsubscribe from $title. Try again."))
    }

    /** Archives the [list]'s older mail, after unsubscribing. */
    fun archiveOlder(
        message: MailMessage,
        list: MailingList,
    ) {
        val newsletters = read.services.newsletters ?: return
        state.setNewsletter(message, NewsletterStep.Archived)
        read.effects.launch {
            val done = newsletters.archive(message.location.accountId, list.olderIds)
            if (!done) state.setNewsletter(message, NewsletterStep.Unsubscribed)
            val count = list.olderIds.size
            toasts.show(
                ToastMessage(if (done) "$count older digests archived" else "Couldn't archive them. Try again."),
            )
        }
    }
}
