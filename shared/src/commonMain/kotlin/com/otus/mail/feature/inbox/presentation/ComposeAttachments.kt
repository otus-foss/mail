package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.elements.RichText
import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.PickedFile
import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The files attached to a draft of [accountId]. Each picked file uploads at once, and again when
 * the draft moves to another account; [attachments] and [change] read and edit the draft's list.
 * A picked image goes into [body] at the cursor; it can switch to a file and back, and deleting it
 * from the text removes it. Without [files] and [uploads], nothing can be attached.
 */
class ComposeAttachments(
    private val files: AttachmentFiles?,
    private val uploads: AttachmentUploads?,
    private val scope: CoroutineScope,
    private val body: RichText,
    private val accountId: () -> String,
    private val attachments: () -> List<DraftAttachment>,
    private val change: ((List<DraftAttachment>) -> List<DraftAttachment>) -> Unit,
) {
    val canAttach: Boolean get() = files != null && uploads != null

    /** Why each attachment's last upload failed, by its id; its chip offers Retry. */
    var failures by mutableStateOf<Map<String, UploadFailure>>(emptyMap())
        private set

    /** Each picked file by its attachment's id, kept to upload it again and to draw it. */
    private val picked = mutableMapOf<String, PickedFile>()

    /** Whether [attachment] is still on its way to the draft's account. */
    fun uploading(attachment: DraftAttachment): Boolean =
        !attachment.uploadedTo(accountId()) && attachment.id !in failures

    /** Lets the user pick files and attaches each of them. */
    fun attach() {
        val from = files ?: return
        scope.launch { from.pick().forEach(::add) }
    }

    fun remove(id: String) {
        attachments().firstOrNull { it.id == id }?.takeIf { it.inline }?.let { body.removeImage(it.source) }
        picked -= id
        failures -= id
        change { list -> list.filterNot { it.id == id } }
    }

    fun retry(id: String) = upload(id)

    /** Shows an image in the body at the cursor, or takes it out and attaches it as a file. */
    fun showInline(
        id: String,
        inline: Boolean,
    ) {
        val attachment = attachments().firstOrNull { it.id == id } ?: return
        change { list -> list.map { if (it.id == id) it.copy(inline = inline) else it } }
        if (inline) body.insertImage(attachment.source) else body.removeImage(attachment.source)
    }

    /** The bytes of the file whose image the body shows at [source], while the app still has them. */
    suspend fun bytes(source: String): ByteArray? =
        attachments()
            .firstOrNull { it.source == source }
            ?.let { picked[it.id] }
            ?.let { runCatching { it.read() }.getOrNull() }

    /**
     * Uploads every file again whenever the draft moves to another account, and removes an inline
     * image the user deleted from the text. Runs until cancelled.
     */
    suspend fun follow() =
        coroutineScope {
            launch {
                snapshotFlow(accountId).drop(1).collect { account ->
                    attachments().filterNot { it.uploadedTo(account) }.forEach { upload(it.id) }
                }
            }
            snapshotFlow { body.imageSources }.drop(1).collect { sources ->
                attachments().filter { it.inline && it.source !in sources }.forEach { remove(it.id) }
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    private fun add(file: PickedFile) {
        val attachment = DraftAttachment(Uuid.random().toString(), file.name, file.type, file.size)
        val shown = attachment.copy(inline = attachment.image)
        picked[shown.id] = file
        change { it + shown }
        if (shown.inline) body.insertImage(shown.source)
        upload(shown.id)
    }

    private fun upload(id: String) {
        val file = picked[id] ?: return
        val to = uploads ?: return
        val account = accountId()
        failures -= id
        scope.launch {
            runCatching { file.read() }
                .fold(
                    onSuccess = { to.upload(account, it, file.type) },
                    onFailure = { Result.failure(UploadException(UploadFailure.Failed, it)) },
                ).fold(
                    // A draft moved on during the upload needs the file in its new account instead.
                    onSuccess = { blob -> if (accountId() == account) uploaded(id, blob) },
                    onFailure = { failures += id to ((it as? UploadException)?.failure ?: UploadFailure.Failed) },
                )
        }
    }

    private fun uploaded(
        id: String,
        blob: AttachmentBlob,
    ) = change { list -> list.map { if (it.id == id) it.copy(blob = blob) else it } }
}

/** Where the body's HTML points at an inline image (RFC 2392). */
internal val DraftAttachment.source: String get() = "cid:$cid"
