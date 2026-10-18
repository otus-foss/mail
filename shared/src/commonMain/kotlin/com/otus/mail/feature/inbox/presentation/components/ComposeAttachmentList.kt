package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AttachmentChip
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.PanelTokens
import com.otus.mail.core.text.sizeText
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.UploadFailure
import com.otus.mail.feature.inbox.presentation.ComposeAttachments

/**
 * A chip for each of [files]: its name and size, "Uploading…" until the draft's account has it,
 * or why it failed. A failed upload tries again on a tap, and ✕ removes the file.
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
                        failure != null -> "Couldn't upload. Tap to retry"
                        attachments.uploading(file) -> "Uploading…"
                        else -> sizeText(file.size)
                    },
                onRemove = { attachments.remove(file.id) },
                onRetry = { attachments.retry(file.id) }.takeIf { failure == UploadFailure.Failed },
            )
        }
    }
}
