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
import androidx.compose.ui.graphics.painter.Painter
import com.otus.designsystem.components.AttachmentChip
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

/**
 * A chip for each of [files]: its name and size, "Uploading…" until the draft's account has it,
 * or why it failed. Tapping an image's chip shows it in the message or attaches it as a file, and
 * a failed upload's chip offers Retry. ✕ removes the file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposeAttachmentList(
    files: List<DraftAttachment>,
    attachments: ComposeAttachments,
) {
    if (files.isEmpty()) return
    FlowRow(
        Modifier.padding(horizontal = PageTokens.textInset, vertical = PanelTokens.chipGap),
        horizontalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
        verticalArrangement = Arrangement.spacedBy(PanelTokens.chipGap),
    ) {
        files.forEach { file ->
            val failure = attachments.failures[file.id]
            AttachmentChip(
                file.name,
                detail =
                    when {
                        failure == UploadFailure.TooLarge -> "Too large for this server"
                        failure != null -> "Couldn't upload"
                        attachments.uploading(file) -> "Uploading…"
                        file.inline -> "In message, ${sizeText(file.size)}"
                        else -> sizeText(file.size)
                    },
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
        when {
            !image -> {
                Unit
            }

            inline -> {
                add(
                    MenuAction("Attach as file", Res.drawable.symbol_attach_file) { attachments.showInline(id, false) },
                )
            }

            else -> {
                add(
                    MenuAction(
                        "Show in message",
                        Res.drawable.symbol_add_photo_alternate,
                    ) { attachments.showInline(id, true) },
                )
            }
        }
    }

/** The picked image the body shows at [source]; null until it is read, or once the app lost it. */
@Composable
internal fun ComposeAttachments.image(source: String): Painter? {
    val bytes by produceState<ByteArray?>(null, source) { value = bytes(source) }
    val bitmap = rememberImage(bytes) ?: return null
    return remember(bitmap) { BitmapPainter(bitmap) }
}
