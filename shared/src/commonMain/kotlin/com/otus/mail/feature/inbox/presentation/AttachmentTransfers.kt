package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.mutableStateMapOf
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.feature.inbox.domain.MailAttachment
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OfflineAttachments
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.StoredFile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Opens, saves, and keeps a page's files. Each goes to the device first; while one downloads, a
 * toast shows how far it is, and its Cancel stops the download. After a file opens, its toast
 * offers to keep it offline, or to remove a kept copy. Work runs through [work], in the
 * process's scope, so it finishes after the page closes.
 */
internal class AttachmentTransfers(
    private val offline: OfflineAttachments?,
    private val saved: SavedFiles?,
    private val toasts: ToastState,
    private val work: (suspend () -> Unit) -> Unit,
) {
    private val kept = mutableStateMapOf<String, Boolean>()

    /** Whether [file] is kept offline, once [loadKept] or a keep has said. */
    fun isKept(
        message: MailMessage,
        file: MailAttachment,
    ): Boolean = kept[key(message, file)] == true

    /** Reads which of [message]'s [files] are kept offline. */
    suspend fun loadKept(
        message: MailMessage,
        files: List<MailAttachment>,
    ) {
        val offline = offline ?: return
        files.forEach { kept[key(message, it)] = offline.isKept(message, it) }
    }

    /** [file]'s bytes for a thumbnail; null when it cannot be had. */
    suspend fun bytes(
        message: MailMessage,
        file: MailAttachment,
    ): ByteArray? = offline?.bytes(message, file)

    fun open(
        message: MailMessage,
        file: MailAttachment,
    ) {
        val saved = saved ?: return
        work {
            val group = Any()
            val stored = download(message, listOf(file), "Downloading ${file.name}", group).single()
            when {
                stored == null -> toasts.show(ToastMessage("Couldn't download ${file.name}", group = group))
                !saved.open(stored) -> toasts.show(ToastMessage("Couldn't open ${file.name}", group = group))
                else -> toasts.show(opened(message, file, stored, group))
            }
        }
    }

    fun save(
        message: MailMessage,
        files: List<MailAttachment>,
    ) {
        val saved = saved ?: return
        // A download can take a while, so the tap answers at once and the outcome replaces it.
        val group = Any()
        val saving = if (files.size == 1) "Saving ${files.single().name}" else "Saving ${files.size} files"
        toasts.show(ToastMessage("$saving…", group = group, sticky = true))
        work {
            val stored = download(message, files, saving, group)
            val failed = files.zip(stored).filterNot { (_, file) -> file != null && saved.save(file) }.map { it.first }
            val text =
                when {
                    failed.isEmpty() -> "Saved to Downloads"
                    failed.size == 1 -> "Couldn't save ${failed.single().name}"
                    else -> "Couldn't save ${failed.size} files"
                }
            toasts.show(ToastMessage(text, group = group))
        }
    }

    /**
     * Each of [files] on the device, in order, or null for one that failed. Once bytes arrive, a
     * sticky toast in [group] shows [label] with the share received of all of them; its Cancel
     * stops the downloads, and this throws the cancellation to the caller.
     */
    private suspend fun download(
        message: MailMessage,
        files: List<MailAttachment>,
        label: String,
        group: Any,
    ): List<StoredFile?> {
        val offline = offline ?: return files.map { null }
        val total = files.sumOf { it.size }.coerceAtLeast(1)
        val received = MutableStateFlow(-1L)
        return coroutineScope {
            val transfer = coroutineContext.job
            val cancel =
                UiAction("Cancel") {
                    transfer.cancel()
                    toasts.show(ToastMessage("Download canceled", group = group))
                }
            val shown =
                launch {
                    received
                        .filter { it >= 0 }
                        .map { percent(it, total) }
                        .distinctUntilChanged()
                        .collect { toasts.show(ToastMessage("$label · $it%", cancel, group, sticky = true)) }
                }
            var done = 0L
            val stored =
                buildList {
                    for (file in files) {
                        add(offline.fetch(message, file) { received.value = done + it })
                        done += file.size
                    }
                }
            shown.cancel()
            stored
        }
    }

    /** The toast after [file] opened: keep it offline, or remove the kept copy. */
    private fun opened(
        message: MailMessage,
        file: MailAttachment,
        stored: StoredFile,
        group: Any,
    ): ToastMessage =
        if (stored.kept) {
            ToastMessage("${file.name} is kept offline", UiAction("Remove") { remove(message, file) }, group)
        } else {
            ToastMessage("Opened ${file.name}", UiAction("Keep offline") { keep(message, file) }, group)
        }

    fun keep(
        message: MailMessage,
        file: MailAttachment,
    ) {
        val offline = offline ?: return
        work {
            val stored = offline.keep(message, file)
            kept[key(message, file)] = stored != null
            val text = if (stored != null) "Kept ${file.name} offline" else "Couldn't keep ${file.name} offline"
            toasts.show(ToastMessage(text))
        }
    }

    fun remove(
        message: MailMessage,
        file: MailAttachment,
    ) {
        val offline = offline ?: return
        work {
            val removed = offline.remove(message, file)
            if (removed) kept[key(message, file)] = false
            val text = if (removed) "Removed ${file.name} from this device" else "Couldn't remove ${file.name}"
            toasts.show(ToastMessage(text))
        }
    }

    private fun key(
        message: MailMessage,
        file: MailAttachment,
    ) = "${message.location.accountId}/${file.blobId}/${file.name}"
}

/** [received] of [total] bytes in whole percent, at most 100. */
private fun percent(
    received: Long,
    total: Long,
): Int = (received * PERCENT / total).coerceAtMost(PERCENT).toInt()

private const val PERCENT = 100L
