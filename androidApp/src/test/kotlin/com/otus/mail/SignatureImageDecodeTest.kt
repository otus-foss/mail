package com.otus.mail

import android.graphics.Bitmap
import com.otus.mail.feature.inbox.domain.SignatureImagePick
import com.otus.mail.feature.inbox.domain.SignatureImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureImageDecodeTest {
    @Test
    fun dimensionsAreBoundedBeforeDecodeForTallAndWideImages() {
        listOf(100_000 to 1, 1 to 100_000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (width, height) ->
            val sample = requireNotNull(sampleSize(width, height, SignatureImages.MAX_WIDTH))
            assertTrue((maxOf(width, height).toLong() + sample - 1) / sample <= SignatureImages.MAX_WIDTH)
        }
        assertNull(sampleSize(-1, -1, SignatureImages.MAX_WIDTH))
        assertNull(sampleSize(0, 100, SignatureImages.MAX_WIDTH))
    }

    @Test
    fun decodedImageFitsTheByteLimit() {
        val source = Bitmap.createBitmap(10, 2000, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, output)
        source.recycle()
        val pick = signatureImage { output.toByteArray().inputStream() }
        val image = (pick as SignatureImagePick.Picked).image
        assertTrue(Base64.decode(image.base64).size <= SignatureImages.MAX_BYTES)
    }

    @Test
    fun invalidDataIsNotAcceptedAsAnImage() {
        assertEquals(SignatureImagePick.Unreadable, signatureImage { byteArrayOf(1, 2, 3).inputStream() })
    }

    @Test
    fun aLowerJpegQualityIsUsedWhenTheFirstDoesNotFit() {
        val noise = noise()
        val firstTry = noise.size(Bitmap.CompressFormat.JPEG, JPEG_START_QUALITY)
        val lowest = noise.size(Bitmap.CompressFormat.JPEG, JPEG_MIN_QUALITY)
        assertTrue(lowest < firstTry && firstTry < noise.size(Bitmap.CompressFormat.PNG, 100))
        val image = (noise.signature(maxBytes = firstTry - 1) as SignatureImagePick.Picked).image
        assertEquals("image/jpeg", image.mediaType)
        assertTrue(Base64.decode(image.base64).size < firstTry)
    }

    @Test
    fun anImageThatFitsNoQualityIsRejected() {
        val noise = noise()
        val lowest = noise.size(Bitmap.CompressFormat.JPEG, JPEG_MIN_QUALITY)
        assertEquals(SignatureImagePick.TooLarge, noise.signature(maxBytes = lowest - 1))
    }

    /** Random opaque pixels, which neither PNG nor JPEG compresses well. */
    private fun noise(): Bitmap {
        val random = Random(SEED)
        val pixels = IntArray(NOISE_SIDE * NOISE_SIDE) { random.nextInt() or OPAQUE }
        return Bitmap.createBitmap(pixels, NOISE_SIDE, NOISE_SIDE, Bitmap.Config.ARGB_8888)
    }

    private fun Bitmap.size(
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Int = ByteArrayOutputStream().also { compress(format, quality, it) }.size()

    private companion object {
        const val SEED = 117
        const val NOISE_SIDE = 200
        const val OPAQUE = 0xFF000000.toInt()
    }
}
