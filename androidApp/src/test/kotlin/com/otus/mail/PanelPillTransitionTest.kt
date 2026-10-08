package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.otus.designsystem.components.ChipPlacement
import com.otus.designsystem.components.ContextPanel
import com.otus.designsystem.elements.Chip
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelPillTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val expanded = mutableStateOf(false)

    @Test
    fun outgoingPillLeavesNoNotchInTheDescendingSurface() {
        showPanel(collapsed = listOf("All unread messages"), wide = listOf("People"))
        val outgoing = compose.onNodeWithText("All unread messages").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        // Just above the long outgoing pill, beyond the short incoming one: only the old cutout was here.
        val x = (outgoing.right - outgoing.height / 2).toInt()
        val y = (outgoing.center.y - (36f / 2 + 1) * density).toInt()
        compose.mainClock.autoAdvance = false
        changeMode(true)
        // The descending edge has passed the sample row; from here it must stay solid surface.
        advanceDrawing(112)
        val incoming = compose.onNodeWithText("People").fetchSemanticsNode().boundsInRoot
        assertTrue("The sample lies beyond the incoming pill", x > incoming.right + 24 * density)
        repeat(10) { frame ->
            assertEquals(
                "Once the edge has passed, the outgoing notch must not linger as a translucent ghost ($frame)",
                OtusColors.bgDark.toArgb(),
                pixel(x, y),
            )
            advanceDrawing(16)
        }
        advanceDrawing(400)
        compose.onNodeWithText("All unread messages").assertDoesNotExist()
        assertEquals("Disposing the outgoing group changes nothing", OtusColors.bgDark.toArgb(), pixel(x, y))
    }

    @Test
    fun incomingCutoutOpensAsSoonAsTheEdgeReachesItsPill() {
        showPanel(collapsed = listOf("Unread"), wide = listOf("People", "Date"))
        val outgoing = compose.onNodeWithText("Unread").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        changeMode(true)
        advanceDrawing(112)
        val incoming = compose.onNodeWithText("Date").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Only the incoming pill sits here", incoming.left > outgoing.right)
        assertEquals(
            "Mid-descent, the edge already wraps the incoming pill's full-size cutout",
            OtusColors.bgRegular.toArgb(),
            pixel(incoming.center.x.toInt(), (incoming.center.y - (36f / 2 + 1) * density).toInt()),
        )
    }

    @Test
    fun incomingCutoutNeverPopsIntoTheSurfaceStillCoveringIt() {
        expanded.value = true
        showPanel(collapsed = listOf("Unread", "Attachments"), wide = listOf("People"))
        val outgoing = compose.onNodeWithText("People").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        changeMode(false)
        advanceDrawing(16)
        val incoming = compose.onNodeWithText("Attachments").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val x = incoming.center.x.toInt()
        val y = (incoming.center.y - (36f / 2 + 1) * density).toInt()
        assertTrue("Only the incoming pill sits here", incoming.left > outgoing.right)
        repeat(6) { frame ->
            assertEquals(
                "The rising surface covers the incoming pill without a hole popping in ($frame)",
                OtusColors.bgDark.toArgb(),
                pixel(x, y),
            )
            advanceDrawing(16)
        }
    }

    @Test
    fun outgoingPillsAreNotClippedWhileTheirGroupNarrows() {
        expanded.value = true
        showPanel(collapsed = listOf("Unread"), wide = listOf("People", "Date", "Mailboxes"))
        val last = compose.onNodeWithText("Mailboxes").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        // Lower right of the pill, below the surface edge and the label: pill fill against background.
        val x = (last.right - 12 * density).toInt()
        val y = (last.center.y + 14 * density).toInt()
        compose.mainClock.autoAdvance = false
        changeMode(false)
        // The group is now narrower than this pill's right end, while the pill is still visible.
        advanceDrawing(112)
        assertNotEquals(
            "A fading pill stays painted instead of being cut off at the group's animated width",
            OtusColors.bgRegular.toArgb(),
            pixel(x, y),
        )
    }

    private fun changeMode(value: Boolean) {
        compose.runOnIdle { expanded.value = value }
        advanceDrawing(16)
    }

    /** Draw every frame, as a device does. Cutouts resolve against the frames that were shown. */
    private fun advanceDrawing(millis: Long) {
        repeat((millis / 16).toInt().coerceAtLeast(1)) {
            compose.mainClock.advanceTimeByFrame()
            pixel(0, 0)
        }
    }

    private fun showPanel(
        collapsed: List<String>,
        wide: List<String>,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) {
                        ContextPanel(
                            placement = if (expanded.value) ChipPlacement.Overlapping else ChipPlacement.Below,
                            chips = {
                                transition(expanded.value) { mode ->
                                    (if (mode) wide else collapsed).forEach { label -> item(label) { Chip(label, {}) } }
                                }
                            },
                        ) { BasicText("Header") }
                    }
                }
            }
        }
    }

    private fun pixel(
        x: Int,
        y: Int,
    ): Int =
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel(x, y).also { bitmap.recycle() }
        }
}
