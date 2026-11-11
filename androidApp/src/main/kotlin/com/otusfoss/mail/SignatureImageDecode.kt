package com.otusfoss.mail

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.otusfoss.mail.core.messages.domain.SignatureImage
import com.otusfoss.mail.core.messages.domain.SignatureImagePick
import com.otusfoss.mail.core.messages.domain.SignatureImages
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.io.encoding.Base64

/** Decodes the image at a bounded size, then shrinks it to fit the signature limits. */
internal fun signatureImage(open: () -> InputStream?): SignatureImagePick =
    try {
        sampledBitmap(open, SignatureImages.MAX_WIDTH)?.let { source ->
            try {
                source.signature()
            } finally {
                source.recycle()
            }
        } ?: SignatureImagePick.Unreadable
    } catch (expected: IOException) {
        SignatureImagePick.Unreadable
    } catch (expected: SecurityException) {
        SignatureImagePick.Unreadable
    }

/** Tries PNG, then JPEG at falling quality, until one fits [maxBytes]. */
internal fun Bitmap.signature(maxBytes: Int = SignatureImages.MAX_BYTES): SignatureImagePick {
    val ratio = minOf(1.0, SignatureImages.MAX_WIDTH.toDouble() / maxOf(width, height))
    val scaled = scale((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
    return try {
        val encoded =
            scaled.encoded(Bitmap.CompressFormat.PNG, FULL_QUALITY, maxBytes)?.let { "image/png" to it }
                ?: JPEG_QUALITIES.firstNotNullOfOrNull { quality ->
                    scaled.encoded(Bitmap.CompressFormat.JPEG, quality, maxBytes)?.let { "image/jpeg" to it }
                }
        encoded?.let { (type, bytes) ->
            val shownWidth = scaled.width.coerceIn(MIN_SHOWN_WIDTH, MAX_SHOWN_WIDTH)
            SignatureImagePick.Picked(SignatureImage(type, Base64.encode(bytes), shownWidth))
        } ?: SignatureImagePick.TooLarge
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

/** A rejected encoding is discarded before trying the next quality. */
private fun Bitmap.encoded(
    format: Bitmap.CompressFormat,
    quality: Int,
    maxBytes: Int,
): ByteArray? {
    val output = ByteArrayOutputStream()
    return if (compress(format, quality, output) && output.size() <= maxBytes) {
        output.toByteArray()
    } else {
        null
    }
}

private const val FULL_QUALITY = 100
internal const val JPEG_START_QUALITY = 85
internal const val JPEG_MIN_QUALITY = 25
private const val JPEG_QUALITY_STEP = 15
private val JPEG_QUALITIES = JPEG_START_QUALITY downTo JPEG_MIN_QUALITY step JPEG_QUALITY_STEP
private const val MIN_SHOWN_WIDTH = 48
private const val MAX_SHOWN_WIDTH = 300
