package com.otus.mail

import android.graphics.Bitmap
import com.otus.mail.feature.inbox.domain.SignatureImages
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureImageDecodeTest {
    @Test
    fun dimensionsAreBoundedBeforeDecodeForTallAndWideImages() {
        listOf(100_000 to 1, 1 to 100_000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (width, height) ->
            val sample = requireNotNull(signatureSample(width, height))
            assertTrue((maxOf(width, height).toLong() + sample - 1) / sample <= SignatureImages.MAX_WIDTH)
        }
        assertNull(signatureSample(-1, -1))
        assertNull(signatureSample(0, 100))
    }

    @Test
    fun decodedImageFitsTheByteLimit() {
        val source = Bitmap.createBitmap(10, 2000, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, output)
        source.recycle()
        val image = requireNotNull(signatureImage { output.toByteArray().inputStream() })
        assertTrue(Base64.decode(image.base64).size <= SignatureImages.MAX_BYTES)
    }

    @Test
    fun invalidDataIsNotAcceptedAsAnImage() {
        assertNull(signatureImage { byteArrayOf(1, 2, 3).inputStream() })
    }
}
