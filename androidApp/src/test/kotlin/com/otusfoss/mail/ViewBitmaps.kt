package com.otusfoss.mail

import android.graphics.Bitmap
import android.graphics.Canvas
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

private const val PNG_QUALITY = 100
