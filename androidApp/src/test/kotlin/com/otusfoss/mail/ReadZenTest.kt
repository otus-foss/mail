package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.material.ProvideTextStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.core.messages.domain.OwnAddresses
import com.otusfoss.mail.feature.read.presentation.ReadDisplay
import com.otusfoss.mail.feature.read.presentation.screens.MessageScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Zen mode in the Read view: the panel's pill hides the banners and strips, and shows them again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadZenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures
    private val zen = mutableStateOf(false)

    private fun showDigest() {
        val context =
            fixtures.read(listOf(fixtures.digest), own = OwnAddresses(mapOf("acc" to listOf(fixtures.alex.address)))) {
                display =
                    ReadDisplay(
                        showQuotes = { false },
                        zen = { zen.value },
                        quickView = display.quickView,
                        setZen = { zen.value = it },
                        today = display.today,
                    )
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        MessageScreen(fixtures.digest, fixtures.actions(), null, context, emptySet(), onClose = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun zenModeHidesTheBannerAndStripsUntilItIsLeft() {
        showDigest()
        compose.waitUntil(TIMEOUT) { exists("Images blocked for privacy") && exists("Mailing list") }
        compose.onNodeWithContentDescription("Zen mode").performClick()
        compose.waitForIdle()
        assertTrue(zen.value)
        compose.onNodeWithText("Images blocked for privacy", substring = true).assertIsNotDisplayed()
        compose.onNodeWithText("Mailing list", substring = true).assertIsNotDisplayed()
        compose.screenshot("read/zen")
        compose.onNodeWithContentDescription("Leave zen mode").performClick()
        compose.waitUntil(TIMEOUT) { exists("Images blocked for privacy") && exists("Mailing list") }
        compose.onNodeWithText("Images blocked for privacy", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Mailing list", substring = true).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
