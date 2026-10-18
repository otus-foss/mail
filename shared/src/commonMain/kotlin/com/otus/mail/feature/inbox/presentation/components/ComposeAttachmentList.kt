package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.otus.designsystem.components.AttachmentChip
import com.otus.designsystem.components.EditableImage
import com.otus.designsystem.components.MenuAction
import com.otus.designsystem.elements.rememberImage
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add_photo_alternate
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.core.text.sizeText
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.UploadFailure
import com.otus.mail.feature.inbox.presentation.ComposeAttachments
import com.otus.mail.feature.inbox.presentation.source

/**
 * A chip for each of [files] the message carries as a file: its name and size, "Uploading…" until
 * the draft's account has it, or why it failed. An inline image shows in the body instead, with
 * the same state over it. Tapping an image's chip shows it in the message, and a failed upload's
 * chip offers Retry. ✕ removes the file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposeAttachmentList(
    files: List<DraftAttachment>,
    attachments: ComposeAttachments,
) {
    val shown = files.filterNot { it.inline }
    if (shown.isEmpty()) return
    FlowRow(
        Modifier.padding(horizontal = PageTokens.textInset, vertical = PanelTokens.chipGap),
        horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        verticalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
    ) {
        shown.forEach { file ->
            val failure = attachments.failures[file.id]
            AttachmentChip(
                file.name,
                detail = file.status(failure, attachments) ?: sizeText(file.size),
                onRemove = { attachments.remove(file.id) },
                actions = file.actions(failure, attachments),
            )
        }
    }
}

private fun DraftAttachment.actions(
    failure: UploadFailure?,
    attachments: ComposeAttachments,
): List<MenuAction> =
    buildList {
        if (failure == UploadFailure.Failed) add(MenuAction("Retry") { attachments.retry(id) })
        if (image) add(if (inline) asFile(attachments) else inMessage(attachments))
    }

private fun DraftAttachment.asFile(attachments: ComposeAttachments) =
    MenuAction("Attach as file", Res.drawable.symbol_attach_file) { attachments.showInline(id, false) }

private fun DraftAttachment.inMessage(attachments: ComposeAttachments) =
    MenuAction("Show in message", Res.drawable.symbol_add_photo_alternate) { attachments.showInline(id, true) }

/** Why a file is not ready to send, or null once the draft's account has it. */
private fun DraftAttachment.status(
    failure: UploadFailure?,
    attachments: ComposeAttachments,
): String? =
    when {
        failure == UploadFailure.TooLarge -> "Too large for this server"
        failure != null -> "Couldn't upload"
        attachments.uploading(this) -> "Uploading…"
        else -> null
    }

/**
 * The picked image the body shows at [source], with its upload state and menu; null until the
 * image is read, or once the app lost it.
 */
@Composable
internal fun ComposeAttachments.image(
    files: List<DraftAttachment>,
    source: String,
): EditableImage? {
    val bytes by produceState<ByteArray?>(null, source) { value = bytes(source) }
    val bitmap = rememberImage(bytes)
    val painter = remember(bitmap) { bitmap?.let(::BitmapPainter) }
    val file = files.firstOrNull { it.source == source }
    if (painter == null || file == null) return null
    val failure = failures[file.id]
    return EditableImage(painter, file.name, file.status(failure, this), file.actions(failure, this))
}
