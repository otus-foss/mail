package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.panels.components.ChipPlacement
import com.otus.designsystem.shared.panels.components.ContextPanel
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.OtusTheme
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
    fun cutoutKeepsItsRoundedCapAboveTheRail() {
        expanded.value = true
        showPanel(chipOffset = (-12).dp)
        val density = compose.activity.resources.displayMetrics.density
        val railTop = surfaceBottom() - (48f / 2 * density).toInt()
        val pill = compose.onNodeWithText("People").fetchSemanticsNode().boundsInRoot
        val bitmap = snapshot()
        assertEquals(
            "The cutout stays rounded instead of clipping flat at the rail top",
            OtusColors.bgRegular.toArgb(),
            bitmap.getPixel(pill.center.x.toInt(), railTop - 2),
        )
        bitmap.recycle()
    }

    @Test
    fun changingModesNeverCutsAnOutgoingNotchAboveTheRestingRail() {
        showPanel()
        val resting = compose.onNodeWithText("Unread").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val x = resting.center.x.toInt()
        val y = (resting.center.y - (48f / 2 + 2) * density).toInt()
        compose.mainClock.autoAdvance = false
        listOf(true, false, true).forEach { target ->
            changeMode(target)
            repeat(20) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val bitmap = snapshot()
                assertEquals(
                    "No departing pill may carve a second, raised notch in frame $frame toward $target",
                    OtusColors.bgDark.toArgb(),
                    bitmap.getPixel(x, y),
                )
                bitmap.recycle()
            }
        }
    }

    @Test
    fun surfaceResizesWhileBothPillGroupsKeepTheSameVerticalAnchor() {
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
        assertEquals(
            originalPill,
            compose
                .onNodeWithText("Unread")
                .fetchSemanticsNode()
                .positionInRoot.y,
            0.5f,
        )
        compose.mainClock.advanceTimeBy(400)
        val open = surfaceBottom()
        val restingPill =
            compose
                .onNodeWithText("People")
                .fetchSemanticsNode()
                .positionInRoot.y
        assertTrue("The surface grows through intermediate heights", growing > closed && growing < open)
        assertEquals("Incoming pills share the resting rail anchor", restingPill, enteringPill, 0.5f)
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
        assertEquals(
            returningPill,
            compose
                .onNodeWithText("Unread")
                .fetchSemanticsNode()
                .positionInRoot.y,
            0.5f,
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

    private fun showPanel(chipOffset: Dp = 0.dp) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) {
                        ContextPanel(
                            placement = if (expanded.value) ChipPlacement.Overlapping else ChipPlacement.Below,
                            chips = {
                                transition(expanded.value) { mode ->
                                    // Reuse the item key to check that disposing one group preserves the other.
                                    item(
                                        "filter",
                                    ) {
                                        Chip(
                                            if (mode) "People" else "Unread",
                                            { taps++ },
                                            Modifier.offset(y = chipOffset),
                                        )
                                    }
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
