package com.otus.mail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.IconButton
import com.otus.designsystem.shared.panels.components.PanelSearchPill
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
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
class ControlPressFeedbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun chipFeedbackStaysInsidePillWhenTouchPaddingIsPressed() = checkFeedback(icon = false)

    @Test
    fun iconFeedbackStaysInsideCircleWhenTouchPaddingIsPressed() = checkFeedback(icon = true)

    @Test
    fun searchPillKeepsItsBackgroundWhenPressed() {
        var clicks = 0
        show { PanelSearchPill("Search Inbox", onClick = { clicks++ }) }
        val prompt = compose.onNodeWithText("Search Inbox")
        val before = screenshot()
        prompt.performTouchInput { down(center) }
        val pressed = screenshot()
        prompt.performTouchInput { up() }
        assertEquals(1, clicks)
        assertTrue("The search pill should not change while pressed", before.sameAs(pressed))
    }

    private fun checkFeedback(icon: Boolean) {
        var clicks = 0
        show {
            if (icon) {
                IconButton("Action", onClick = { clicks++ }) {}
            } else {
                Chip("Filter", onClick = { clicks++ })
            }
        }
        val control = if (icon) compose.onNodeWithContentDescription("Action") else compose.onNodeWithText("Filter")
        val bounds = control.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 48f)
        val before = screenshot()
        // Press inside the accessible target but above the visible 36 dp surface.
        control.performTouchInput { down(Offset(center.x, 2f)) }
        val pressed = screenshot()
        control.performTouchInput { up() }
        assertEquals(1, clicks)
        var changedInside = 0
        val visualWidth = if (icon) 36f else bounds.width
        val left = bounds.center.x - visualWidth / 2
        val right = bounds.center.x + visualWidth / 2
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val centerX = (x + 0.5f).coerceIn(left + 18f, right - 18f)
                val dx = x + 0.5f - centerX
                val dy = y + 0.5f - bounds.center.y
                val distanceSquared = dx * dx + dy * dy
                val original = before.getPixel(x, y)
                val actual = pressed.getPixel(x, y)
                // Leave one pixel for antialiasing at the rounded edge.
                if (distanceSquared > 19f * 19f) {
                    assertEquals("Feedback outside visual control at $x,$y", original, actual)
                } else if (distanceSquared < 17f * 17f && original != actual) {
                    changedInside++
                }
            }
        }
        assertTrue("The visible control should show press feedback", changedInside > 0)
        val released = screenshot()
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                assertEquals(before.getPixel(x, y), released.getPixel(x, y))
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) { content() }
                }
            }
        }
    }

    private fun screenshot(): Bitmap = compose.windowBitmap()
}
