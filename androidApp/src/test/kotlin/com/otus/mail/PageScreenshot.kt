package com.otus.mail

import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * Writes the activity's window with any open menu, dropdown, or sheet, which draw in windows of
 * their own, laid over it, to `build/reports/compose/<name>.png`.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.screenshot(name: String) {
    val bitmap =
        runOnIdle {
            val decor = activity.window.decorView
            val snapshot = decor.drawnBitmap()
            val canvas = Canvas(snapshot)
            windowViews().filter { it !== decor && it.isShown }.forEach { view ->
                val position = IntArray(2)
                view.getLocationOnScreen(position)
                canvas.save()
                canvas.translate(position[0].toFloat(), position[1].toFloat())
                view.draw(canvas)
                canvas.restore()
            }
            snapshot
        }
    bitmap.savePng("build/reports/compose/$name.png")
}

/** Every window's root view, the page's and each popup's, from the framework's window list. */
private fun windowViews(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
    return views.filterIsInstance<View>()
}
