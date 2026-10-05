package com.otus.mail.designsystem.elements

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/** Image identity for decoding/state tests, without a platform bitmap allocation. */
class AvatarTestImage : ImageBitmap {
    override val width = 1
    override val height = 1
    override val colorSpace = ColorSpaces.Srgb
    override val hasAlpha = true
    override val config = ImageBitmapConfig.Argb8888

    override fun prepareToDraw() = Unit

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = error("Decoding/state tests do not render pixels")
}
