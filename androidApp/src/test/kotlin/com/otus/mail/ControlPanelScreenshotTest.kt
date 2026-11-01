package com.otus.mail

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.tokens.layout.PanelTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each state of the control panel design, in the Otus theme, and the inbox and Read view in the other themes too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlPanelScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun inboxInEveryTheme() =
        ThemeBase.entries.forEach { base ->
            compose.showControlInbox(palette(base))
            compose.panelShot("inbox_top_${base.name.lowercase()}")
        }

    @Test
    fun inboxHalfCollapsed() {
        compose.showControlInbox()
        compose.holdScrolledBy(PanelTokens.collapseDistance / 2)
        compose.panelShot("inbox_collapse_50")
    }

    @Test
    fun inboxScrolled() {
        compose.showControlInbox()
        compose.onRoot().performTouchInput { swipeUp(startY = centerY + 300, endY = centerY - 300) }
        compose.waitForIdle()
        compose.panelShot("inbox_scrolled")
    }

    @Test
    fun search() {
        compose.showControlInbox()
        compose.onNodeWithContentDescription("Search Inbox").performClick()
        compose.searchField().performTextInput("launch")
        compose.waitForIdle()
        compose.panelShot("search")
    }

    @Test
    fun filters() {
        compose.showControlInbox()
        compose.onNodeWithContentDescription("Open filters", substring = true).performClick()
        compose.waitForIdle()
        compose.panelShot("filters")
    }

    @Test
    fun selecting() {
        compose.showControlInbox()
        compose.onNodeWithText("Q4 launch plan").performTouchInput { longClick() }
        compose.onNodeWithText("Notes from Thursday").performClick()
        compose.onNodeWithText("Launch numbers").performClick()
        compose.waitForIdle()
        compose.panelShot("selecting")
    }

    @Test
    fun accountSwitcher() {
        compose.showControlInbox()
        compose.onNode(hasContentDescription("Showing mail from", substring = true)).performClick()
        compose.waitForIdle()
        compose.panelShot("accounts")
    }

    @Test
    fun readInEveryTheme() =
        ThemeBase.entries.forEach { base ->
            compose.showControlRead(palette(base))
            compose.panelShot("read_${base.name.lowercase()}")
        }

    @Test
    fun settingsSearch() {
        compose.withStatusBar()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.panelShot("settings")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.mainClock.advanceTimeBy(SEARCH_HALF_MILLIS)
        compose.panelShot("settings_search_50")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.panelShot("settings_search_100")
    }

    private companion object {
        /** Half of the search field's 300 ms growth at the Regular speed. */
        const val SEARCH_HALF_MILLIS = 150L
    }
}
