package com.otusfoss.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * The activity's window once the UI is idle, without menus or sheets, which have windows of their
 * own, for tests that compare pixels; [screenshot] compares whole screens with saved images.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.windowBitmap(): Bitmap =
    runOnIdle {
        val window = activity.window.decorView
        Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }

internal fun Bitmap.pngBytes(): ByteArray = encoded(Bitmap.CompressFormat.PNG, PNG_QUALITY)

/** A small two-tone picture as PNG bytes, standing in for a downloaded photo. */
internal fun twoTonePhoto(): ByteArray {
    val bitmap = Bitmap.createBitmap(PHOTO_SIZE, PHOTO_SIZE, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.rgb(0x3b, 0x5a, 0x6e))
        drawCircle(
            PHOTO_SIZE * 0.6f,
            PHOTO_SIZE * 0.4f,
            PHOTO_SIZE * 0.25f,
            Paint().apply { color = Color.rgb(0xe8, 0xc4, 0x6a) },
        )
    }
    return bitmap.pngBytes()
}

private const val PNG_QUALITY = 100
private const val PHOTO_SIZE = 96
