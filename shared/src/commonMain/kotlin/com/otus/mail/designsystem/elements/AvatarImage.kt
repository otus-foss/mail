package com.otus.mail.designsystem.elements

import androidx.compose.ui.graphics.ImageBitmap

/** Platform raster decoding only. The caller supplies bytes; no network access occurs in the UI. */
expect fun decodeAvatarImage(bytes: ByteArray): ImageBitmap?
