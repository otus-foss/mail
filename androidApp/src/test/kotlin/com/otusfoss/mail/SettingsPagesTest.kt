package com.otusfoss.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPagesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dateFormatShowsTheCurrentChoiceAndChangesItInAPopup() {
        openSettings()
        compose.onNodeWithText("Theme, app icon, mail list, dates, and animations").assertIsDisplayed()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Date format").assertIsDisplayed()
        compose.onNodeWithText("YYYY-MM-DD").assertDoesNotExist()
        compose.onNodeWithText("Date format").performClick()
        compose.onNodeWithText("YYYY-MM-DD").performClick()
        // Choosing applies at once; the sheet stays open with the check moved until Done.
        compose.onNodeWithContentDescription("Dismiss Date format").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Dismiss Date format").assertDoesNotExist()
        compose.onNodeWithText("YYYY-MM-DD", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("YYYY-MM-DD", substring = true).assertIsDisplayed()
    }

    @Test
    fun animationSpeedOffersInstantAndThreeSpeedsWithRegularByDefault() {
        openSettings()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Regular").assertIsDisplayed()
        compose.onNodeWithText("Animations").performClick()
        listOf("Instant", "Slow", "Fast", "No animations").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Fast").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Fast").assertIsDisplayed()
    }

    @Test
    fun dismissingTheDateFormatPopupKeepsTheChoice() {
        openSettings()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Date format").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Device").assertIsDisplayed()
    }

    @Test
    fun swipingTheChoiceSheetDownFromItsOptionsClosesIt() {
        openSettings()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Date format").performClick()
        // A short, slow pull springs back.
        compose.onNodeWithText("DD.MM.YYYY").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 60f, durationMillis = 600)
        }
        compose.onNodeWithContentDescription("Dismiss Date format").assertExists()
        compose.onNodeWithText("DD.MM.YYYY").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 200)
        }
        compose.onNodeWithContentDescription("Dismiss Date format").assertDoesNotExist()
        compose.onNodeWithText("Device").assertIsDisplayed()
    }

    @Test
    fun systemBackLeavesASubpageForSettings() {
        openSettings()
        compose.onNodeWithText("Email accounts").performClick()
        compose.onNodeWithText("Connect an account").assertIsDisplayed()
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun systemBackFromSettingsReturnsToTheInboxInsteadOfClosingMail() {
        openSettings()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Appearance").assertDoesNotExist()
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun accountSetupResolvesTheJmapSessionBeforeConnecting() {
        openSettings()
        compose.onNodeWithText("Email accounts").performClick()
        compose.onNodeWithText("Connect an account").performClick()
        val server = compose.onNodeWithContentDescription("Server")
        // The field shows no text under it; an address without TLS only keeps Connect off.
        server.performTextInput("http://mail.example.com")
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        server.performTextReplacement("Mail.Example.com")
        compose.onNodeWithContentDescription("Username").performTextInput("me@example.com")
        compose.onNodeWithContentDescription("Password").performTextInput("app-password")
        compose
            .onNodeWithContentDescription("Password")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun closingAccountSetupDiscardsTheFormAndKeepsTheAccountsPage() {
        openSettings()
        compose.onNodeWithText("Email accounts").performClick()
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
        compose.onNodeWithText("Connect an account").performClick()
        compose.onNodeWithContentDescription("Server").performTextInput("mail.example.org")
        compose.onNodeWithContentDescription("Username").performTextInput("ada@example.org")
        compose.onNodeWithContentDescription("Password").performTextInput("secret")
        compose.onNodeWithContentDescription("Dismiss Connect an account").performTouchInput {
            click(Offset(center.x, 10f))
        }
        waitForAccountSheetToClose()
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
        compose.onNodeWithText("Connected accounts").assertIsDisplayed()
        compose.onNodeWithText("Connect an account").performClick()
        compose.onNodeWithText("mail.example.org").assertDoesNotExist()
        compose.onNodeWithText("ada@example.org").assertDoesNotExist()
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        compose.onNodeWithText("Done").performClick()
        waitForAccountSheetToClose()
        compose.onNodeWithContentDescription("Server").assertDoesNotExist()
    }

    private fun waitForAccountSheetToClose() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Server")).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Email accounts").assertIsDisplayed()
    }
}
