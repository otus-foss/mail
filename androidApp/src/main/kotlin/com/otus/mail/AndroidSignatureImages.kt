package com.otus.mail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.core.graphics.scale
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64

/**
 * Picks signature images with the system photo picker of the activity in front, then shrinks them
 * to at most [SignatureImages.MAX_WIDTH] pixels wide and about [SignatureImages.MAX_BYTES] bytes:
 * PNG when that is small enough, so transparency survives, and JPEG at falling quality otherwise.
 */
class AndroidSignatureImages(
    private val context: Context,
) : SignatureImages {
    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /** Registers the picker for [activity]; call it from `onCreate`, before the activity starts. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(PickVisualMedia()) { pending?.complete(it) }
    }

    override suspend fun pick(): SignatureImage? {
        val picker = launcher ?: return null
        val result = CompletableDeferred<Uri?>().also { pending = it }
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        return result.await()?.let { uri -> withContext(Dispatchers.IO) { shrink(uri) } }
    }

    private fun shrink(uri: Uri): SignatureImage? {
        val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
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

    private fun Bitmap.encoded(
        format: Bitmap.CompressFormat,
        quality: Int,
    ): ByteArray = ByteArrayOutputStream().also { compress(format, quality, it) }.toByteArray()

    private companion object {
        const val FULL_QUALITY = 100
        val JPEG_QUALITIES = listOf(85, 70, 55, 40, 25)

        /** The width a new image shows at, inside the editor's resize limits. */
        const val MIN_SHOWN_WIDTH = 48
        const val MAX_SHOWN_WIDTH = 300
    }
}
