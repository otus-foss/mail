package com.otus.mail.designsystem.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/** Platform raster decoding only. The caller supplies bytes; no network access occurs in the UI. */
expect fun decodeAvatarImage(bytes: ByteArray): ImageBitmap?

/** Decodes off the main thread. Rows that scroll back into view reuse the decoded bitmap. */
@Composable
fun rememberAvatarImage(bytes: ByteArray?): ImageBitmap? = rememberAvatarImage(bytes, decodedAvatars)

@Composable
internal fun rememberAvatarImage(
    bytes: ByteArray?,
    cache: DecodedAvatarCache,
): ImageBitmap? {
    val image = remember(bytes, cache) { mutableStateOf(bytes?.let(cache::get)) }
    LaunchedEffect(bytes, cache) {
        image.value = bytes?.let { cache.load(it) }
    }
    return image.value
}

private val decodedAvatars = DecodedAvatarCache()
