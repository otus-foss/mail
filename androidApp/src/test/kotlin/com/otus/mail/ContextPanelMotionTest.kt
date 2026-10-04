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
import androidx.compose.ui.test.performClick
import com.otus.mail.designsystem.components.ChipPlacement
import com.otus.mail.designsystem.components.ContextPanel
import com.otus.mail.designsystem.elements.Chip
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.OtusTheme
import org.junit.Assert.assertEquals
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
class ContextPanelMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val expanded = mutableStateOf(false)
    private var taps = 0

    @Test
    fun surfaceAndPillsMoveThroughIntermediatePositionsInBothDirections() {
        showPanel()
        val closed = surfaceBottom()
        val originalPill =
            compose
                .onNodeWithText("Unread")
                .fetchSemanticsNode()
                .positionInRoot.y
        compose.mainClock.autoAdvance = false
        changeMode(true)
        compose.mainClock.advanceTimeBy(64)
        val growing = surfaceBottom()
        val enteringPill =
            compose
                .onNodeWithText("People")
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue(
            compose
                .onNodeWithText("Unread")
                .fetchSemanticsNode()
                .positionInRoot.y < originalPill,
        )
        compose.mainClock.advanceTimeBy(400)
        val open = surfaceBottom()
        val restingPill =
            compose
                .onNodeWithText("People")
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("The surface grows through intermediate heights", growing > closed && growing < open)
        assertTrue("Incoming pills move into place", enteringPill > restingPill)
        changeMode(false)
        compose.mainClock.advanceTimeBy(64)
        val shrinking = surfaceBottom()
        val returningPill =
            compose
                .onNodeWithText("Unread")
                .fetchSemanticsNode()
                .positionInRoot.y
        compose.mainClock.advanceTimeBy(400)
        assertTrue("The surface shrinks through intermediate heights", shrinking > closed && shrinking < open)
        assertEquals(closed, surfaceBottom())
        assertTrue(
            returningPill <
                compose
                    .onNodeWithText("Unread")
                    .fetchSemanticsNode()
                    .positionInRoot.y,
        )
    }

    @Test
    fun interruptedTransitionKeepsFinalCutoutAndPillInteraction() {
        showPanel()
        compose.mainClock.autoAdvance = false
        changeMode(true)
        compose.mainClock.advanceTimeBy(80)
        changeMode(false)
        compose.mainClock.advanceTimeBy(48)
        changeMode(true)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Unread").assertDoesNotExist()
        val pill = compose.onNodeWithText("People").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val gapY = (pill.center.y - (36f / 2 + 1) * density).toInt()
        val bitmap = snapshot()
        assertEquals(
            "The final pill retains its cutout",
            OtusColors.bgRegular.toArgb(),
            bitmap.getPixel(pill.center.x.toInt(), gapY),
        )
        bitmap.recycle()
        compose.onNodeWithText("People").performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }

    private fun changeMode(value: Boolean) {
        compose.runOnIdle { expanded.value = value }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun showPanel() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) {
                        ContextPanel(
                            placement = if (expanded.value) ChipPlacement.Overlapping else ChipPlacement.Below,
                            chips = {
                                transition(expanded.value) { mode ->
                                    // Reuse the item key to check that disposing one group preserves the other.
                                    item("filter") { Chip(if (mode) "People" else "Unread", { taps++ }) }
                                }
                            },
                        ) { BasicText("Header") }
                    }
                }
            }
        }
    }

    private fun surfaceBottom(): Int {
        val bitmap = snapshot()
        val x = bitmap.width / 2
        val bottom = (0 until bitmap.height).last { bitmap.getPixel(x, it) == OtusColors.bgDark.toArgb() }
        bitmap.recycle()
        return bottom
    }

    private fun snapshot(): Bitmap =
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
}
