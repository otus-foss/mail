package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** This view drawn into a new bitmap of its size. */
internal fun View.drawnBitmap(): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

/** The activity's window as drawn now, without menus or sheets, which have windows of their own. */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.drawnWindow(): Bitmap =
    activity.window.decorView.drawnBitmap()

/** The activity's window once the UI is idle, as [drawnWindow] draws it. */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.windowBitmap(): Bitmap = runOnIdle { drawnWindow() }

/** The window of the dialog shown last, such as a sheet or a picker, once the UI is idle. */
internal fun AndroidComposeTestRule<*, *>.dialogBitmap(): Bitmap =
    runOnIdle { requireNotNull(ShadowDialog.getLatestDialog().window).decorView.drawnBitmap() }

internal fun Bitmap.pngBytes(): ByteArray = encoded(Bitmap.CompressFormat.PNG, PNG_QUALITY)

/** Writes this bitmap as a PNG to [path], creating its folder. */
internal fun Bitmap.savePng(path: String) {
    val file = File(path)
    requireNotNull(file.parentFile).mkdirs()
    file.outputStream().use { compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
}

private const val PNG_QUALITY = 100
