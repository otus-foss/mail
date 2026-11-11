package com.otusfoss.mail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The system photo picker of the activity in front. [pick] returns a way to open the chosen photo,
 * or null when the user cancels, so callers can read its size before decoding it with [sampledBitmap].
 */
class AndroidPhotoPicker(
    private val context: Context,
) {
    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /** Registers the picker for [activity]; call it from `onCreate`, before the activity starts. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(PickVisualMedia()) { pending?.complete(it) }
    }

    suspend fun pick(): (() -> InputStream?)? {
        val picker = launcher ?: return null
        val result = CompletableDeferred<Uri?>()
        pending = result
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        return result.await()?.let { uri -> { context.contentResolver.openInputStream(uri) } }
    }
}

/** Reads dimensions first, then decodes at a sample size that keeps both sides near [maxSide]. */
internal fun sampledBitmap(
    open: () -> InputStream?,
    maxSide: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) ?: return null
    return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}

/** The smallest power of two that brings the longest side to at most [maxSide], or null without a size. */
internal fun sampleSize(
    width: Int,
    height: Int,
    maxSide: Int,
): Int? {
    if (width <= 0 || height <= 0) return null
    val longest = maxOf(width, height).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > maxSide) sample *= 2
    return sample
}

fun Bitmap.encoded(
    format: Bitmap.CompressFormat,
    quality: Int,
): ByteArray {
    val output = ByteArrayOutputStream()
    compress(format, quality, output)
    return output.toByteArray()
}
