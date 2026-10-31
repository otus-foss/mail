package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The inbox panel follows the list's scroll and settles; a subpage's search grows over its title and back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theSearchPillTightensWithTheScrollAndReversesExactly() {
        compose.showControlInbox()
        assertPillHeight(PanelTokens.searchPillHeight)
        compose.holdScrolledBy(PanelTokens.collapseDistance)
        assertPillHeight(PanelTokens.compactSearchPillHeight)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertPillHeight(PanelTokens.compactSearchPillHeight)
        compose.onRoot().performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertPillHeight(PanelTokens.searchPillHeight)
    }

    @Test
    fun aReleaseHalfwaySettlesOnTheNearerState() {
        compose.showControlInbox()
        slowDrag(PanelTokens.collapseDistance / 4)
        assertPillHeight(PanelTokens.searchPillHeight)
        slowDrag(PanelTokens.collapseDistance * 3 / 4)
        assertPillHeight(PanelTokens.compactSearchPillHeight)
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

    private fun slowDrag(distance: Dp) {
        compose.onRoot().performTouchInput {
            val travel = distance.toPx() + viewConfiguration.touchSlop
            swipe(center, center - Offset(0f, travel), durationMillis = SLOW_DRAG_MILLIS)
        }
        compose.waitForIdle()
    }

    private fun assertPillHeight(expected: Dp) {
        val bounds = compose.onNodeWithContentDescription("Search Inbox").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(expected.value, bounds.height / density, HEIGHT_TOLERANCE)
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
        const val HEIGHT_TOLERANCE = 1f
    }
}
