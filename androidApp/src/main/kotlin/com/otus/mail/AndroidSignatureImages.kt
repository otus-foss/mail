package com.otus.mail

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

/**
 * Picks signature images with [photos], then shrinks them to at most [SignatureImages.MAX_WIDTH]
 * pixels wide and about [SignatureImages.MAX_BYTES] bytes: PNG when that is small enough, so
 * transparency survives, and JPEG at falling quality otherwise.
 */
class AndroidSignatureImages(
    private val photos: AndroidPhotoPicker,
) : SignatureImages {
    override suspend fun pick(): SignatureImage? =
        photos.pick()?.let { withContext(Dispatchers.Default) { shrink(it) } }

    private fun shrink(source: Bitmap): SignatureImage {
        val width = minOf(source.width, SignatureImages.MAX_WIDTH)
        val scaled = source.scale(width, source.height * width / source.width)
        val png = scaled.encoded(Bitmap.CompressFormat.PNG, FULL_QUALITY)
        val (type, bytes) =
            if (png.size <= SignatureImages.MAX_BYTES) {
                "image/png" to png
            } else {
                "image/jpeg" to
                    (
                        JPEG_QUALITIES.map { scaled.encoded(Bitmap.CompressFormat.JPEG, it) }.firstOrNull {
                            it.size <=
                                SignatureImages.MAX_BYTES
                        }
                            ?: scaled.encoded(Bitmap.CompressFormat.JPEG, JPEG_QUALITIES.last())
                    )
            }
        return SignatureImage(type, Base64.encode(bytes), width.coerceIn(MIN_SHOWN_WIDTH, MAX_SHOWN_WIDTH))
    }

    private companion object {
        const val FULL_QUALITY = 100
        val JPEG_QUALITIES = listOf(85, 70, 55, 40, 25)

        /** The width a new image shows at, inside the editor's resize limits. */
        const val MIN_SHOWN_WIDTH = 48
        const val MAX_SHOWN_WIDTH = 300
    }
}
