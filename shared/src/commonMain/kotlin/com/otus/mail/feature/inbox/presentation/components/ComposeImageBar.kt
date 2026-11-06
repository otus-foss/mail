package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.designsystem.mail.compose.components.ImageBar
import com.otus.designsystem.mail.compose.components.selectedImageSizing
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_add_photo_alternate
import com.otus.designsystem.resources.symbol_arrow_upward
import com.otus.designsystem.resources.symbol_attach_file
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_short_text
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.overlays.components.InputSheet
import com.otus.designsystem.shared.overlays.components.OptionsItem
import com.otus.mail.core.files.UploadFailure
import com.otus.mail.core.messages.domain.DraftAttachment
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.source

/**
 * The bar while an image in the body is selected: its sizes, ✓ to finish, and ⋯ with Send as
 * attachment, Description, Replace, and Remove image, and Retry after a failed upload.
 */
@Composable
internal fun ComposeImageBar(state: ComposeState) {
    val body = state.body
    val source = body.images.selected ?: return
    val sizing = body.selectedImageSizing() ?: return
    val file = state.draft.attachments.firstOrNull { it.source == source }
    var describing by remember(source) { mutableStateOf(false) }
    ImageBar(sizing, file?.options(state) { describing = true }.orEmpty(), onDone = body.images::deselect)
    if (describing) {
        InputSheet(
            "Image description",
            "Description",
            "Save",
            onConfirm = { body.images.describe(source, it) },
            onDismiss = { describing = false },
            initial = body.images.description(source),
        )
    }
}

private fun DraftAttachment.options(
    state: ComposeState,
    onDescribe: () -> Unit,
): List<OptionsItem> {
    val attachments = state.attachments
    return buildList {
        if (attachments.failures[id] == UploadFailure.Failed) {
            add(
                OptionsItem.Action(
                    UiAction("Retry upload", Res.drawable.symbol_arrow_upward, onClick = { attachments.retry(id) }),
                ),
            )
        }
        add(
            OptionsItem.Toggle("Send as file", Res.drawable.symbol_attach_file, checked = false) {
                state.body.images.deselect()
                attachments.showInline(id, inline = false)
            },
        )
        add(
            OptionsItem.Action(
                UiAction("Description", Res.drawable.symbol_short_text, onClick = onDescribe),
                opens = true,
            ),
        )
        add(
            OptionsItem.Action(
                UiAction("Replace", Res.drawable.symbol_add_photo_alternate, onClick = {
                    attachments.replace(id)
                }),
            ),
        )
        add(OptionsItem.Divider)
        add(
            OptionsItem.Action(
                UiAction("Remove image", Res.drawable.symbol_delete, onClick = {
                    state.body.images.deselect()
                    attachments.remove(id)
                }),
            ),
        )
    }
}
