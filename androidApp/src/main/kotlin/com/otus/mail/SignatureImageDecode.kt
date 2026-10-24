package com.otus.mail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureImages
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.io.encoding.Base64

/** Reads dimensions first, then decodes at a sample size that bounds both bitmap dimensions. */
internal fun signatureImage(open: () -> InputStream?): SignatureImage? =
    try {
        val source = sampledSignatureBitmap(open)
        if (source == null) {
            null
        } else {
            try {
                source.signature()
            } finally {
                source.recycle()
            }
        }
    } catch (expected: IOException) {
        null
    } catch (expected: SecurityException) {
        null
    }

private fun sampledSignatureBitmap(open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = signatureSample(bounds.outWidth, bounds.outHeight) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return open()?.use { BitmapFactory.decodeStream(it, null, options) }
}

internal fun signatureSample(
    width: Int,
    height: Int,
): Int? {
    if (width <= 0 || height <= 0) return null
    val longest = maxOf(width, height).toLong()
    var sample = 1
    while ((longest + sample - 1) / sample > SignatureImages.MAX_WIDTH) sample *= 2
    return sample
}

private fun Bitmap.signature(): SignatureImage? {
    val ratio = minOf(1.0, SignatureImages.MAX_WIDTH.toDouble() / maxOf(width, height))
    val scaled = scale((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
    return try {
        val png = scaled.encoded(Bitmap.CompressFormat.PNG, FULL_QUALITY)
        val encoded =
            if (png != null) {
                "image/png" to png
            } else {
                JPEG_QUALITIES.firstNotNullOfOrNull { quality ->
                    scaled.encoded(Bitmap.CompressFormat.JPEG, quality)?.let { "image/jpeg" to it }
                }
            }
        encoded?.let { (type, bytes) ->
            SignatureImage(type, Base64.encode(bytes), scaled.width.coerceIn(MIN_SHOWN_WIDTH, MAX_SHOWN_WIDTH))
        }
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

/** A rejected encoding is discarded before trying the next quality. */
private fun Bitmap.encoded(
    format: Bitmap.CompressFormat,
    quality: Int,
): ByteArray? {
    val output = ByteArrayOutputStream()
    return if (compress(format, quality, output) &&
        output.size() <= SignatureImages.MAX_BYTES
    ) {
        output.toByteArray()
    } else {
        null
    }
}

private const val FULL_QUALITY = 100
private const val JPEG_START_QUALITY = 85
private const val JPEG_MIN_QUALITY = 25
private const val JPEG_QUALITY_STEP = 15
private val JPEG_QUALITIES = JPEG_START_QUALITY downTo JPEG_MIN_QUALITY step JPEG_QUALITY_STEP
private const val MIN_SHOWN_WIDTH = 48
private const val MAX_SHOWN_WIDTH = 300
