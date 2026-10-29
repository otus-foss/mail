package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.designsystem.mail.compose.elements.RichText
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.PickedFile
import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The files attached to a draft of [accountId]. Each picked file uploads at once, and again when
 * the draft moves to another account; [attachments] and [change] read and edit the draft's list.
 * A picked image goes into [body] at the cursor; it can switch to a file and back, and deleting it
 * from the text removes it. Without [files] and [uploads], nothing can be attached. Photos lose their
 * metadata and names as the services' cleaning choice says, which [cleaning] may ask about first.
 */
class ComposeAttachments(
    services: ComposeAttachmentServices,
    private val scope: CoroutineScope,
    private val body: RichText,
    private val accountId: () -> String,
    private val attachments: () -> List<DraftAttachment>,
    private val change: ((List<DraftAttachment>) -> List<DraftAttachment>) -> Unit,
    initialSources: Map<String, PickedFile> = emptyMap(),
) {
    private val files = services.files
    private val uploads = services.uploads
    val canAttach: Boolean get() = files != null && uploads != null

    /** Whether picked photos lose their metadata and names, which may ask the user first. */
    val cleaning = PhotoCleaningQuestion(services.cleaning)

    /** Why each attachment's last upload failed, by its id; its chip offers Retry. */
    var failures by mutableStateOf<Map<String, UploadFailure>>(emptyMap())
        private set

    /** Each picked file by its attachment's id, kept to upload it again and to draw it. */
    private val picked = initialSources.toMutableMap()
    private val jobs = mutableMapOf<String, Job>()

    /** Sources stay available to Undo, without copying their file bytes into the draft. */
    val sourceFiles: Map<String, PickedFile> get() = picked.toMap()

    /** A closed composer must not publish upload results into a later editing session. */
    fun stop() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    // One file uploads at a time, so its bytes stay in memory until the server has them. Drawing
    // reads separately, so an inline image shows while uploads are still queued.
    private val uploadLock = Mutex()
    private val drawLock = Mutex()

    /** Whether [attachment] is still on its way to the draft's account. */
    fun uploading(attachment: DraftAttachment): Boolean =
        !attachment.uploadedTo(accountId()) && attachment.id !in failures

    /** Lets the user pick files and attaches each of them. */
    fun attach() {
        val from = files ?: return
        scope.launch { cleaning.cleanedAsChosen(from.pick()).forEach(::add) }
    }

    fun remove(id: String) {
        attachments().firstOrNull { it.id == id }?.takeIf { it.inline }?.let { body.images.remove(it.source) }
        forget(id)
        change { list -> list.filterNot { it.id == id } }
    }

    /** Shows an image in the body at the cursor, or takes it out and attaches it as a file. */
    fun showInline(
        id: String,
        inline: Boolean,
    ) {
        val attachment = attachments().firstOrNull { it.id == id } ?: return
        update(id) { it.copy(inline = inline) }
        if (inline) body.images.insert(attachment.source) else body.images.remove(attachment.source)
    }

    /** Lets the user pick another image to show where the image of [id] is, at its size. */
    fun replace(id: String) {
        val from = files ?: return
        scope.launch {
            val chosen = from.pick().firstOrNull { it.type.startsWith("image/") } ?: return@launch
            val file = cleaning.cleanedAsChosen(listOf(chosen)).single()
            val old = attachments().firstOrNull { it.id == id } ?: return@launch
            val new = newAttachment(file).copy(inline = true)
            forget(id)
            picked[new.id] = file
            update(id) { new }
            body.images.replace(old.source, new.source)
            retry(new.id)
        }
    }

    /** The bytes of the file whose image the body shows at [source], while the app still has them. */
    suspend fun bytes(source: String): ByteArray? =
        attachments()
            .firstOrNull { it.source == source }
            ?.let { picked[it.id] }
            ?.let { file ->
                drawLock.withLock {
                    try {
                        file.read()
                    } catch (expected: UploadException) {
                        null
                    }
                }
            }

    /**
     * Uploads every file again whenever the draft moves to another account, and removes an inline
     * image the user deleted from the text. Runs until cancelled.
     */
    suspend fun follow() =
        coroutineScope {
            launch {
                snapshotFlow(accountId).collect { account ->
                    attachments().filterNot { it.uploadedTo(account) }.forEach { retry(it.id) }
                }
            }
            snapshotFlow { body.images.sources }.drop(1).collect { sources ->
                attachments().filter { it.inline && it.source !in sources }.forEach { remove(it.id) }
            }
        }

    /** Stops [id]'s upload and drops its picked file and failure. */
    private fun forget(id: String) {
        jobs.remove(id)?.cancel()
        picked -= id
        failures -= id
    }

    private fun update(
        id: String,
        transform: (DraftAttachment) -> DraftAttachment,
    ) = change { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun add(file: PickedFile) {
        val attachment = newAttachment(file)
        val shown = attachment.copy(inline = attachment.image)
        picked[shown.id] = file
        change { it + shown }
        if (shown.inline) body.images.insert(shown.source)
        retry(shown.id)
    }

    /** Uploads the picked file again, replacing any earlier attempt. */
    fun retry(id: String) {
        val file = picked[id] ?: return
        val to = uploads ?: return
        val account = accountId()
        failures -= id
        jobs.remove(id)?.cancel()
        jobs[id] =
            scope.launch {
                val result =
                    uploadLock.withLock {
                        if (account != accountId() || id !in picked) return@launch
                        try {
                            val limit = to.maxBytes(account)
                            to.upload(account, file.read(limit), file.type)
                        } catch (failed: UploadException) {
                            Result.failure(failed)
                        }
                    }
                currentCoroutineContext().ensureActive()
                // A draft moved on during the upload needs the file in its new account instead.
                if (accountId() != account) return@launch
                result.fold(
                    onSuccess = { blob -> update(id) { it.copy(blob = blob) } },
                    onFailure = { failures += id to ((it as? UploadException)?.failure ?: UploadFailure.Failed) },
                )
            }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun newAttachment(file: PickedFile) = DraftAttachment(Uuid.random().toString(), file.name, file.type, file.size)

/** Where the body's HTML points at an inline image (RFC 2392). */
internal val DraftAttachment.source: String get() = "cid:$cid"

/**
 * The platform picker and account uploader used by the attachment editor, and whether attached
 * photos lose their metadata and names; without a [cleaning] choice, files attach as picked.
 */
class ComposeAttachmentServices(
    val files: AttachmentFiles?,
    val uploads: AttachmentUploads?,
    val cleaning: () -> PhotoCleaning = { PhotoCleaning.Never },
)
