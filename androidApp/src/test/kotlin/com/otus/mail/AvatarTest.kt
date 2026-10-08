package com.otus.mail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.otus.designsystem.elements.Avatar
import com.otus.designsystem.elements.decodeAvatarImage
import com.otus.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun decodedPhotoReplacesInitialsAndIsDownsampled() {
        val bitmap = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        val image = decodeAvatarImage(output.toByteArray())
        assertNotNull(image)
        assertEquals(256, image?.width)
        assertEquals(128, image?.height)
        compose.runOnUiThread {
            compose.activity.setContent { OtusTheme { Avatar("AL", image = image) } }
        }
        compose.onNodeWithText("AL").assertDoesNotExist()
    }

    @Test
    fun pngEncodedFaviconIsDecoded() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        val png = output.toByteArray()
        val ico =
            ByteBuffer
                .allocate(22 + png.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0)
                .putShort(1)
                .putShort(1)
                .put(32)
                .put(32)
                .put(0)
                .put(0)
                .putShort(1)
                .putShort(32)
                .putInt(png.size)
                .putInt(22)
                .put(png)
                .array()
        val image = decodeAvatarImage(ico)
        assertNotNull(image)
        assertEquals(32, image?.width)
        assertEquals(32, image?.height)
    }

    @Test
    fun bitmapEncodedFaviconIsDecoded() {
        val ico =
            ByteBuffer
                .allocate(86)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0)
                .putShort(1)
                .putShort(1)
                .put(2)
                .put(2)
                .put(0)
                .put(0)
                .putShort(1)
                .putShort(32)
                .putInt(64)
                .putInt(22)
                .putInt(40)
                .putInt(2)
                .putInt(4)
                .putShort(1)
                .putShort(32)
                .putInt(0)
                .putInt(16)
                .putInt(0)
                .putInt(0)
                .putInt(0)
                .putInt(0)
                .putInt(android.graphics.Color.BLUE)
                .putInt(android.graphics.Color.BLUE)
                .putInt(android.graphics.Color.BLUE)
                .putInt(android.graphics.Color.BLUE)
                .putLong(0)
                .array()
        val image = decodeAvatarImage(ico)
        assertNotNull(image)
        assertEquals(2, image?.width)
        assertEquals(2, image?.height)
    }

    @Test
    fun malformedPhotoRetainsVisibleInitials() {
        val image = decodeAvatarImage(byteArrayOf(1, 2, 3))
        assertNull(image)
        compose.runOnUiThread {
            compose.activity.setContent { OtusTheme { Avatar("AL", image = image) } }
        }
        compose.onNodeWithText("AL").assertIsDisplayed()
    }
}
