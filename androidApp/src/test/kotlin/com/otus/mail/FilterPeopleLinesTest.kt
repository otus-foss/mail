package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.otus.mail.core.messages.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.InboxFilters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the People card's From and To lines do as the user types, with goldens in src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterPeopleLinesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun typingInFromListsTheMatches() {
        compose.showFilterPanel(InboxFilters(from = setOf(FilterPanelFixtures.grace)))
        compose.onNodeWithContentDescription("From").performTextInput("p")
        compose.waitForIdle()
        compose.onNodeWithText("Lena Park").assertIsDisplayed()
        // The matches open in a popup of their own, so the shot takes every window.
        compose.screenshot("filter_panel_from_typing")
    }

    @Test
    fun tappingAMatchPicksThemAndEmptiesTheLine() {
        compose.showFilterPanel()
        compose.onNodeWithContentDescription("From").performTextInput("chat")
        compose.waitForIdle()
        compose.onNodeWithText("ChatGPT").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("noreply@openai.example, updates@openai.example").assertIsDisplayed()
        compose.onNodeWithContentDescription("From").assertTextEquals("")
    }

    @Test
    fun tappingAChipPutsTheirAddressBackInTheLine() {
        compose.showFilterPanel()
        compose.onNodeWithContentDescription("From").performTextInput("new@x.example")
        compose.onNodeWithContentDescription("From").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("new@x.example").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("From").assertTextEquals("new@x.example")
    }

    @Test
    fun enterPicksATypedAddress() {
        compose.showFilterPanel()
        compose.onNodeWithContentDescription("From").performTextInput("new@x.example")
        compose.onNodeWithContentDescription("From").performImeAction()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("new@x.example").assertIsDisplayed()
    }

    @Test
    fun receivedInShowsOnceSomeoneIsPickedInTo() {
        compose.showFilterPanel()
        compose.onNodeWithText("Received in").assertDoesNotExist()
        compose.onNodeWithContentDescription("To").performTextInput("rubylabs")
        compose.waitForIdle()
        compose.onNodeWithText("otus@rubylabs.design").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Received in").assertIsDisplayed()
    }

    @Test
    fun receivedInUnderTheToLine() {
        compose.showFilterPanel(InboxFilters(sentTo = setOf(OwnAddress("ada", "otus@rubylabs.design"))))
        compose.screenshot("filter_panel_received_in")
    }

    @Test
    fun tappingTheFromPillRemovesIt() {
        compose.showFilterPanel(InboxFilters(from = setOf(FilterPanelFixtures.grace)), open = false)
        compose.onNodeWithContentDescription("From Grace Hopper").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("From Grace Hopper").assertDoesNotExist()
    }
}
