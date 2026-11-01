package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusTheme
import com.otus.settings.presentation.screens.SettingsScreen
import com.otus.settings.presentation.settingsIndex
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class SharedSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsRendersOnlyTheEntriesAndCallbacksSuppliedByTheApp() {
        var opened = 0
        var navigation = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    SettingsScreen(
                        settingsIndex { page("Preferences", { opened++ }, summary = "Choose your defaults") },
                        onOpenNavigation = { navigation++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Choose your defaults").assertIsDisplayed()
        compose.onNodeWithText("Email accounts").assertDoesNotExist()
        compose.onNodeWithText("Folders").assertDoesNotExist()
        compose.onNodeWithText("Preferences").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals(1, navigation)
        }
    }
}
