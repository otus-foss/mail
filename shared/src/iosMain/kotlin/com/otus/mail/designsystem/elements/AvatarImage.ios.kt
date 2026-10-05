package com.otus.mail.designsystem.elements

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeAvatarImage(bytes: ByteArray): ImageBitmap? =
    runCatching {
        val image = Image.makeFromEncoded(bytes)
        if (image.width !in 1..MAX_IMAGE_DIMENSION || image.height !in 1..MAX_IMAGE_DIMENSION) {
            image.close()
            null
        } else {
            image.toComposeImageBitmap()
        }
    }.getOrNull()

private const val MAX_IMAGE_DIMENSION = 4096
