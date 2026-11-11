package com.otusfoss.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import com.otusfoss.core.designsystem.theme.tokens.layout.PanelTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sign

/** The inbox panel follows the list's scroll and settles; a subpage's search grows over its title and back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun thePanelCollapsesWithTheScrollAndReversesExactly() {
        compose.showControlInbox()
        assertCollapsed(false)
        compose.holdScrolledBy(PanelTokens.collapseDistance)
        assertCollapsed(true)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertCollapsed(true)
        // Only as far back as it scrolled: further down, the list's top pulls down the quick view.
        slowDrag(-PanelTokens.collapseDistance)
        assertCollapsed(false)
    }

    @Test
    fun aReleaseHalfwaySettlesOnTheNearerState() {
        compose.showControlInbox()
        slowDrag(PanelTokens.collapseDistance / 4)
        assertCollapsed(false)
        slowDrag(PanelTokens.collapseDistance * 3 / 4)
        assertCollapsed(true)
    }

    @Test
    fun searchGrowsLeftOverTheTitleAndClosesBackward() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.mainClock.advanceTimeBy(EARLY_MILLIS)
        val early = fieldLeft()
        compose.mainClock.advanceTimeBy(EARLY_MILLIS * 2)
        val later = fieldLeft()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val open = fieldLeft()
        assertTrue("The field grows leftward: $early, $later, $open", early > later && later > open)
        compose.onNodeWithText("Email accounts").assertExists()

        compose.onNodeWithContentDescription("Close search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search settings").assertExists()
        compose.onNodeWithText("Settings").assertExists()
    }

    /** Drags the list [distance] up, or down when negative, past the touch slop. */
    private fun slowDrag(distance: Dp) {
        compose.onRoot().performTouchInput {
            val travel = distance.toPx() + viewConfiguration.touchSlop * sign(distance.value)
            swipe(center, center - Offset(0f, travel), durationMillis = SLOW_DRAG_MILLIS)
        }
        compose.waitForIdle()
    }

    /** Collapsed, the filter button sits inside the search pill rather than in the pill row below it. */
    private fun assertCollapsed(expected: Boolean) {
        val pill = compose.onNodeWithContentDescription("Search Inbox").fetchSemanticsNode().boundsInRoot
        val inPill =
            compose
                .onAllNodesWithContentDescription("Open filters")
                .fetchSemanticsNodes()
                .any { pill.contains(it.boundsInRoot.center) }
        assertEquals(expected, inPill)
    }

    private fun fieldLeft(): Float =
        compose
            .onNodeWithContentDescription("Search settings")
            .fetchSemanticsNode()
            .boundsInRoot.left

    private companion object {
        const val EARLY_MILLIS = 60L

        /** Slow enough to fling little, and quick enough past the slop not to count as a long press. */
        const val SLOW_DRAG_MILLIS = 600L
    }
}
