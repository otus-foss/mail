package com.otus.mail.designsystem.elements

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keyed by byte-array identity. Call from the composition thread; only decoding leaves that thread.
 * Active rows share a decode, and retry if the row that started it leaves composition.
 */
internal class DecodedAvatarCache(
    private val maxEntries: Int = 64,
    private val decode: suspend (ByteArray) -> ImageBitmap? = { bytes ->
        withContext(Dispatchers.Default) { decodeAvatarImage(bytes) }
    },
) {
    private val images = linkedMapOf<ByteArray, DecodedImage>()
    private val inFlight = mutableMapOf<ByteArray, CompletableDeferred<DecodedImage?>>()

    init {
        require(maxEntries > 0)
    }

    fun get(bytes: ByteArray): ImageBitmap? = recent(bytes)?.image

    suspend fun load(bytes: ByteArray): ImageBitmap? {
        var entry: DecodedImage? = null
        while (entry == null) {
            val cached = recent(bytes)
            val pending = inFlight[bytes]
            // A null entry means the owner was cancelled, rather than a failed image decode.
            entry =
                when {
                    cached != null -> cached
                    pending != null -> pending.await()
                    else -> decodeShared(bytes)
                }
        }
        return entry.image
    }

    private fun recent(bytes: ByteArray): DecodedImage? = images.remove(bytes)?.also { images[bytes] = it }

    private suspend fun decodeShared(bytes: ByteArray): DecodedImage {
        val pending = CompletableDeferred<DecodedImage?>()
        inFlight[bytes] = pending
        var decoded: DecodedImage? = null
        try {
            val entry = DecodedImage(decode(bytes))
            images[bytes] = entry
            while (images.size > maxEntries) images.remove(images.keys.first())
            decoded = entry
            return entry
        } finally {
            inFlight.remove(bytes)
            pending.complete(decoded)
        }
    }

    private class DecodedImage(
        val image: ImageBitmap?,
    )
}
