package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.otus.designsystem.components.ImageBar
import com.otus.designsystem.components.ImagePreset
import com.otus.designsystem.components.ImageSizing
import com.otus.designsystem.components.OptionsItem
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImageSize
import com.otus.mail.feature.inbox.presentation.SignatureEditorState

/** The bar while a signature image is selected: its sizes, ✓ to finish, and ⋯ with Remove image. */
@Composable
internal fun SignatureImageBar(editor: SignatureEditorState) {
    val index = editor.selected ?: return
    val image = editor.images.getOrNull(index) ?: return
    ImageBar(
        editor.imageSizing(index, image),
        listOf(OptionsItem.Action("Remove image", Res.drawable.symbol_delete, { editor.removeImage(index) })),
        onDone = { editor.selected = null },
    )
}

/** The sizes a signature image offers, shared by the image and its bar. */
internal fun SignatureEditorState.imageSizing(
    index: Int,
    image: SignatureImage,
): ImageSizing = ImageSizing(image.width.dp, presets) { resize(index, it.value.toInt()) }

private val presets = SignatureImageSize.entries.map { ImagePreset(it.label, it.width.dp) }
