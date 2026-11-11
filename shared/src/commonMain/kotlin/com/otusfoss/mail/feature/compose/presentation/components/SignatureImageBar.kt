package com.otusfoss.mail.feature.compose.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.otusfoss.core.designsystem.mail.compose.components.ImageBar
import com.otusfoss.core.designsystem.mail.compose.components.ImagePreset
import com.otusfoss.core.designsystem.mail.compose.components.ImageSizing
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.overlays.components.OptionsItem
import com.otusfoss.mail.core.messages.domain.SignatureImage
import com.otusfoss.mail.core.messages.domain.SignatureImageSize
import com.otusfoss.mail.feature.compose.presentation.SignatureEditorState

/** The bar while a signature image is selected: its sizes, ✓ to finish, and ⋯ with Remove image. */
@Composable
internal fun SignatureImageBar(editor: SignatureEditorState) {
    val index = editor.selected ?: return
    val image = editor.images.getOrNull(index) ?: return
    ImageBar(
        editor.imageSizing(index, image),
        listOf(
            OptionsItem.Action(
                UiAction("Remove image", Res.drawable.symbol_delete, onClick = {
                    editor.removeImage(index)
                }),
            ),
        ),
        onDone = { editor.selected = null },
    )
}

/** The sizes a signature image offers, shared by the image and its bar. */
internal fun SignatureEditorState.imageSizing(
    index: Int,
    image: SignatureImage,
): ImageSizing = ImageSizing(image.width.dp, presets) { resize(index, it.value.toInt()) }

private val presets = SignatureImageSize.entries.map { ImagePreset(it.label, it.width.dp) }
