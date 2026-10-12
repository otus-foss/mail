package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun chevronOpensTheRailBesideMailboxesWithSettingsAtTheBottom() {
        compose.onNodeWithText("All accounts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("All accounts").assertIsDisplayed()
        compose.onNodeWithText("No accounts yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("All accounts").assertIsSelected()
        compose.onNode(hasText("Inbox") and isToggleable()).assertIsDisplayed().assertIsOn()
        val inbox = compose.onNode(hasText("Inbox") and isToggleable()).fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
        val width = compose.activity.window.decorView.width
        assertTrue(
            "Settings sits at the bottom of the rail",
            settings.top > inbox.bottom + width && settings.right < inbox.left,
        )
        val sidebarWidth = with(compose.density) { 336.dp.toPx() }
        assertTrue("The sidebar is 336 dp wide and leaves the page visible", inbox.right < sidebarWidth)
    }

    @Test
    fun choosingSettingsShowsItAndInboxKeepsItsSearch() {
        compose.onNodeWithText("Tap to search").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("invoice")
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("All accounts").assertDoesNotExist()
        compose.onNodeWithText("Email accounts").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isToggleable()).assertIsOff().performClick()
        compose.onNodeWithText("Tap to search").assertIsDisplayed()
    }

    @Test
    fun scrimAndBackGestureCloseTheSidebar() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        // Tap the visible scrim beside the panel, as a user would.
        compose
            .onNodeWithContentDescription(
                "Close navigation",
            ).performTouchInput { click(centerRight - Offset(24f, 0f)) }
        compose.onNodeWithText("All accounts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("All accounts").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("All accounts").assertDoesNotExist()
        compose.onNodeWithText("Tap to search").assertIsDisplayed()
    }

    @Test
    fun swipingOnTheContextPanelOpensTheSidebarAndSwipingBackClosesIt() {
        val panel = compose.onNodeWithContentDescription("Open navigation").fetchSemanticsNode().boundsInRoot
        swipeOpenAt(panel.center.y)
        compose.onNodeWithText("All accounts").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeLeft(width * 0.5f, width * 0.05f) }
        compose.onNodeWithText("All accounts").assertDoesNotExist()
    }

    @Test
    fun swipingAboveThePanelOpensButSwipingOnTheMessageListDoesNot() {
        swipeOpenAt(compose.activity.window.decorView.height * 0.6f)
        compose.onNodeWithText("All accounts").assertDoesNotExist()
        swipeOpenAt(2f)
        compose.onNodeWithText("All accounts").assertIsDisplayed()
    }

    @Test
    fun openingTheSidebarFromSearchClosesTheKeyboard() {
        compose.onNodeWithText("Tap to search").performClick()
        val search = compose.onNodeWithContentDescription("Search your mailbox…")
        search.assertIsFocused()
        swipeOpenAt(2f)
        compose.onNodeWithText("No accounts yet").assertIsDisplayed()
        search.assertIsNotFocused()
    }

    private fun swipeOpenAt(y: Float) {
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.2f, y), Offset(width * 0.8f, y)) }
    }
}
