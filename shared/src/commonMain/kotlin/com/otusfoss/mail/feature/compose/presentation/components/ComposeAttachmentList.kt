package com.otusfoss.mail.feature.compose.presentation.components

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
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentChip
import com.otusfoss.core.designsystem.mail.compose.components.EditableImage
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_add_photo_alternate
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.people.elements.rememberImage
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.mail.core.files.UploadFailure
import com.otusfoss.mail.core.messages.domain.DraftAttachment
import com.otusfoss.mail.core.text.sizeText
import com.otusfoss.mail.feature.compose.presentation.ComposeAttachments
import com.otusfoss.mail.feature.compose.presentation.source

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
        Modifier.padding(horizontal = PageTokens.textInset, vertical = Spacing.inlineGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.inlineGap),
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
): List<UiAction> =
    buildList {
        if (failure == UploadFailure.Failed) add(UiAction("Retry") { attachments.retry(id) })
        if (image) add(inMessage(attachments))
    }

private fun DraftAttachment.inMessage(attachments: ComposeAttachments) =
    UiAction("Show in message", Res.drawable.symbol_add_photo_alternate) { attachments.showInline(id, inline = true) }

/** Why a file is not ready to send, or null once the draft's account has it. */
private fun DraftAttachment.status(
    failure: UploadFailure?,
    attachments: ComposeAttachments,
): String? =
    when {
        failure == UploadFailure.TooLarge -> "File is too large"
        failure != null -> "Couldn't upload"
        attachments.uploading(this) -> "Uploading…"
        else -> null
    }

/**
 * The picked image the body shows at [source], with its upload state; null until the image is
 * read, or once the app lost it.
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
    return EditableImage(painter, file.name, file.status(failures[file.id], this))
}
