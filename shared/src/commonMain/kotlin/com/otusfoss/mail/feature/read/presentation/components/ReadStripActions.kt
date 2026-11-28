package com.otusfoss.mail.feature.read.presentation.components

import androidx.compose.ui.graphics.ImageBitmap
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentActions
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.people.elements.decodeImage
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailingList
import com.otusfoss.mail.core.messages.domain.Unsubscribe
import com.otusfoss.mail.core.messages.presentation.drawable
import com.otusfoss.mail.feature.read.domain.UnsubscribeResult
import com.otusfoss.mail.feature.read.presentation.AttachmentTransfers
import com.otusfoss.mail.feature.read.presentation.NewsletterStep
import com.otusfoss.mail.feature.read.presentation.ReadContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the strips below a page do with its files and its mailing list. Downloads and requests run
 * in the process's scope, so they finish after the page closes, and say how they went in a toast.
 * A file is downloaded to the device once, for its thumbnail, Open, and Save alike; see
 * [AttachmentTransfers]. [openLink] opens a web page outside the app.
 */
internal class ReadStripActions(
    private val read: ReadContext,
    private val openLink: (String) -> Unit,
) {
    private val toasts get() = read.effects.toasts
    private val newsletters get() = read.effects.newsletters
    private val transfers =
        AttachmentTransfers(read.services.files.downloads, read.services.files.saved, toasts, read.effects.launch)

    /** Opening, saving, and saving every one of [files] of [message]. */
    fun files(
        message: MailMessage,
        files: List<MailAttachment>,
    ): AttachmentActions =
        AttachmentActions(
            onOpen = { index -> transfers.open(message, files[index]) },
            onSave = { index -> transfers.save(message, listOf(files[index])) },
            onSaveAll = { transfers.save(message, files) },
        )

    /** Whether [file] is kept offline, once [loadKept] has read it. */
    fun isKept(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = transfers.isKept(message, file)

    /** Reads which of [message]'s [files] are kept offline. */
    suspend fun loadKept(
        message: MailMessage,
        files: List<MailAttachment>,
    ) = transfers.loadKept(message, files)

    /**
     * A small picture of [file] for the strip, when it is an image small enough to download just
     * for that: up to [THUMBNAIL_MAX_BYTES] on Wi-Fi, [METERED_THUMBNAIL_MAX_BYTES] on mobile data
     * or with Data Saver on. Null for other files or when it cannot be had. An image without a
     * size is skipped, because only a known size keeps the download within the limit.
     */
    suspend fun thumbnail(
        message: MailMessage,
        file: MailAttachment,
    ): ImageBitmap? =
        file
            .takeIf { it.drawable() && it.size in 1..thumbnailLimit() }
            ?.let { transfers.bytes(message, it) }
            ?.let { withContext(Dispatchers.Default) { decodeImage(it, THUMBNAIL_PIXELS) } }

    private fun thumbnailLimit(): Long {
        val network = read.services.files.network
        return if (network.metered || network.dataSaver) METERED_THUMBNAIL_MAX_BYTES else THUMBNAIL_MAX_BYTES
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
    ) {
        val lists = read.services.newsletters ?: return
        val web = how.web
        if (!how.takesRequest && web != null) return openLink(web)
        newsletters.set(message, NewsletterStep.Unsubscribed)
        val undo = UiAction("Undo") { newsletters.set(message, NewsletterStep.Subscribed) }
        val send = {
            read.effects.launch {
                when (val result = lists.unsubscribe(message, how)) {
                    UnsubscribeResult.Done -> Unit
                    is UnsubscribeResult.OpenLink -> finishOnTheWeb(message, result.url)
                    UnsubscribeResult.Failed -> failed(message)
                }
            }
        }
        toasts.show(ToastMessage("Unsubscribed", undo, onClose = send))
    }

    /** The list took no request, so the user is subscribed until they finish on its page. */
    private fun finishOnTheWeb(
        message: MailMessage,
        url: String,
    ) {
        newsletters.set(message, NewsletterStep.Subscribed)
        openLink(url)
    }

    private fun failed(message: MailMessage) {
        newsletters.set(message, NewsletterStep.Subscribed)
        toasts.show(ToastMessage("Couldn't unsubscribe. Try again."))
    }

    /**
     * Archives the [list]'s older mail, after unsubscribing. The strip goes at once and the mail
     * moves only when the Undo toast closes, as with [unsubscribe].
     */
    fun archiveOlder(
        message: MailMessage,
        list: MailingList,
    ) {
        val lists = read.services.newsletters ?: return
        newsletters.set(message, NewsletterStep.Archived)
        // A failed unsubscribe may have put the strip back to Subscribed meanwhile; that state wins.
        val restore = {
            if (newsletters.of(message) == NewsletterStep.Archived) {
                newsletters.set(message, NewsletterStep.Unsubscribed)
            }
        }
        val undo = UiAction("Undo", onClick = restore)
        val archive = {
            read.effects.launch {
                if (!lists.archive(message.location.accountId, list.olderIds)) {
                    restore()
                    toasts.show(ToastMessage("Couldn't archive them. Try again."))
                }
            }
        }
        toasts.show(ToastMessage("Archived", undo, onClose = archive))
    }
}

/** The largest image the strip downloads only to show its picture, on Wi-Fi, which fits most phone photos. */
private const val THUMBNAIL_MAX_BYTES = 20L * 1024 * 1024

/** The same on mobile data or with Data Saver on, which mostly fits screenshots. */
private const val METERED_THUMBNAIL_MAX_BYTES = 2L * 1024 * 1024

/** Enough pixels for a 40 dp tile on a dense screen. */
private const val THUMBNAIL_PIXELS = 160
