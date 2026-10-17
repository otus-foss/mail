package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import java.io.File

/**
 * Writes the activity's window with any open menu, dropdown, or sheet, which draw in windows of
 * their own, laid over it, to `build/reports/compose/<name>.png`.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.screenshot(name: String) {
    val bitmap =
        runOnIdle {
            val decor = activity.window.decorView
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                decor.draw(canvas)
                windowViews().filter { it !== decor && it.isShown }.forEach { view ->
                    val at = IntArray(2).also(view::getLocationOnScreen)
                    canvas.save()
                    canvas.translate(at[0].toFloat(), at[1].toFloat())
                    view.draw(canvas)
                    canvas.restore()
                }
            }
        }
    val file = File("build/reports/compose/$name.png")
    requireNotNull(file.parentFile).mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
}

/** Every window's root view, the page's and each popup's, from the framework's window list. */
private fun windowViews(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
    return views.filterIsInstance<View>()
}

private const val PNG_QUALITY = 100
