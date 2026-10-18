package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.AttachmentUploads
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.PickedFile
import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The files attached to a draft of [accountId]. Each picked file uploads at once, and again when
 * the draft moves to another account; [attachments] and [change] read and edit the draft's list.
 * Without [files] and [uploads], nothing can be attached.
 */
class ComposeAttachments(
    private val files: AttachmentFiles?,
    private val uploads: AttachmentUploads?,
    private val scope: CoroutineScope,
    private val accountId: () -> String,
    private val attachments: () -> List<DraftAttachment>,
    private val change: ((List<DraftAttachment>) -> List<DraftAttachment>) -> Unit,
) {
    val canAttach: Boolean get() = files != null && uploads != null

    /** Why each attachment's last upload failed, by its id; tapping its chip tries again. */
    var failures by mutableStateOf<Map<String, UploadFailure>>(emptyMap())
        private set

    /** Each picked file by its attachment's id, kept to upload it again to another account. */
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
        picked -= id
        failures -= id
        change { list -> list.filterNot { it.id == id } }
    }

    fun retry(id: String) = upload(id)

    /** Uploads every file again whenever the draft moves to another account. Runs until cancelled. */
    suspend fun followAccount() {
        snapshotFlow(accountId).drop(1).collect { account ->
            attachments().filterNot { it.uploadedTo(account) }.forEach { upload(it.id) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun add(file: PickedFile) {
        val attachment = DraftAttachment(Uuid.random().toString(), file.name, file.type, file.size)
        picked[attachment.id] = file
        change { it + attachment }
        upload(attachment.id)
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
