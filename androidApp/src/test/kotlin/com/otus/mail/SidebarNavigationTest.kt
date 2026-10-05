package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
    fun chevronHalfOpensSidebarWithNameEntriesAndSettingsAtTheBottom() {
        compose.onNodeWithText("Otus").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Otus").assertIsDisplayed()
        compose.onNodeWithText("Inbox").assertIsDisplayed().assertIsSelected()
        val settings =
            compose
                .onNodeWithText("Settings")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val inbox = compose.onNodeWithText("Inbox").fetchSemanticsNode().boundsInRoot
        val width = compose.activity.window.decorView.width
        assertTrue("Settings sits at the bottom, below the entries", settings.top > inbox.bottom + width)
        assertTrue("The sidebar half-opens and leaves the page visible", inbox.right < width * 0.6f)
    }

    @Test
    fun choosingSettingsShowsItAndInboxKeepsItsSearch() {
        compose.onNodeWithText("Tap to search").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("invoice")
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Settings") and isSelectable()).performClick()
        compose.onNodeWithText("Otus").assertDoesNotExist()
        compose.onNodeWithText("Email accounts").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Settings") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Inbox").performClick()
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
        compose.onNodeWithText("Otus").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Otus").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Otus").assertDoesNotExist()
        compose.onNodeWithText("Tap to search").assertIsDisplayed()
    }
}
