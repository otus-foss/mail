package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
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
class InboxHeaderActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingPromptFocusesSearchRequestsKeyboardAndRevealsSearchSettings() {
        var keyboardRequested = false
        val keyboard =
            object : SoftwareKeyboardController {
                override fun show() {
                    keyboardRequested = true
                }

                override fun hide() = Unit
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    OtusTheme { InboxScreen(emptyList()) }
                }
            }
        }
        compose.onNodeWithText("Tap to search").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").assertIsFocused()
        compose.onNodeWithText("People").assertIsDisplayed()
        compose.onNodeWithText("Date").assertIsDisplayed()
        compose.onNodeWithContentDescription("All accounts").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertTrue("Opening search requests the software keyboard", keyboardRequested) }
    }

    @Test
    fun defaultFilterButtonOpensFiltersWithoutStartingSearch() {
        compose.onNodeWithContentDescription("Open filters").assertIsDisplayed().performClick()
        compose.onNodeWithText("Unread only").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Tap to search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search your mailbox…").assertDoesNotExist()
        compose.onNodeWithText("Unread").assertIsOn()
    }

    @Test
    fun navigationChevronIsAvailableAndSearchBackReturnsToPrompt() {
        compose.onNodeWithContentDescription("Open navigation").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Tap to search").performClick()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithText("Tap to search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").assertIsEnabled()
    }
}
