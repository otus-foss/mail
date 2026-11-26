package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.feature.read.presentation.screens.MessageScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The images banner above a message whose remote images are blocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class ReadImagesBannerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures
    private val toasts = ToastState()

    @Test
    fun theBannersShowLoadsTheImagesAtOnce() {
        val context = fixtures.read(listOf(fixtures.digest), toasts = toasts)
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        MessageScreen(fixtures.digest, fixtures.actions(), null, context, emptySet(), onClose = {})
                    }
                }
            }
        }
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Images blocked for privacy").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Show") and hasClickAction()).performClick()
        compose.onNodeWithText("Images shown").assertIsDisplayed()
        assertTrue(toasts.messages.isEmpty())
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
