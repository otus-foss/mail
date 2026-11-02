package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.FilterLayout
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings that only apply while another one is set stay on their page, disabled with the reason,
 * and come back as soon as that setting changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DependentSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openSettings(behavior: MailBehaviorState) {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
    }

    private fun row(title: String) = compose.onNode(hasText(title) and hasClickAction())

    /** A disabled row, found by its title and the reason under it. */
    private fun waiting(
        title: String,
        reason: String,
    ) = compose.onNode(hasText(title) and hasText(reason))

    @Test
    fun quickFiltersWaitUntilThePanelShowsTheirSection() {
        val sections = FilterSection.entries - FilterSection.Quick
        openSettings(MailBehaviorState(initial = MailBehavior(filters = FilterLayout(sections = sections))))
        compose.onNodeWithText("Mail behavior").performClick()
        row("Filter panel").performScrollTo().performClick()
        waiting("Quick filters", QUICK_REASON).assertIsDisplayed().assertIsNotEnabled()
        row("Sections").performClick()
        compose.onNode(hasText("Quick filters") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        row("Quick filters").assertIsEnabled()
        compose.onNodeWithText(QUICK_REASON).assertDoesNotExist()
    }

    @Test
    fun orderAndPreviewWaitForTheSender() {
        openSettings(MailBehaviorState())
        compose.onNodeWithText("Appearance").performClick()
        row("Mail list").performScrollTo().performClick()
        row("Order").assertIsEnabled()
        compose.onNode(hasText("Show sender") and isToggleable()).performScrollTo().performClick()
        // The rows stay where they were, so the page keeps its shape.
        waiting("Order", SENDER_REASON).assertIsDisplayed().assertIsNotEnabled()
        waiting("Preview", SENDER_REASON).assertIsNotEnabled()
        compose.onNode(hasText("Show sender") and isToggleable()).performClick()
        row("Preview").assertIsEnabled()
        compose.onNodeWithText(SENDER_REASON).assertDoesNotExist()
    }

    private companion object {
        const val QUICK_REASON = "Show the Quick filters section to choose them"
        const val SENDER_REASON = "Turn on Show sender to change this"
    }
}
