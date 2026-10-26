package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Privacy: when remote images load, the allowed senders, and website icons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacySettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior =
        MailBehaviorState(
            initial = MailBehavior(allowedSenders = listOf("news@sentry.io", "grace@example.org")),
        )

    private fun openPrivacy() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Remote images and website icons").assertIsDisplayed()
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Load remote images").assertIsDisplayed()
    }

    @Test
    fun knownPeopleIsTheDefaultAndAChoiceIsSavedAtOnce() {
        openPrivacy()
        compose.onNodeWithText("From people you know").assertIsSelected()
        compose.screenshot("2026-10-19-feat-read-view-privacy")
        compose.onNodeWithText("Never").performClick()
        assertEquals(RemoteImages.Never, behavior.behavior.remoteImages)
        compose.onNodeWithText("Never").assertIsSelected()
        compose.onNodeWithText("From people you know").assertIsNotSelected()
    }

    @Test
    fun removingAnAllowedSenderMakesItAskAgain() {
        openPrivacy()
        compose.onNodeWithText("2 senders").performClick()
        compose.onNodeWithText("news@sentry.io").assertIsDisplayed()
        compose.screenshot("2026-10-19-feat-read-view-allowed-senders")
        compose.onNodeWithContentDescription("Remove news@sentry.io").performClick()
        assertEquals(listOf("grace@example.org"), behavior.behavior.allowedSenders)
        compose.onNodeWithContentDescription("Remove grace@example.org").performClick()
        compose
            .onNodeWithText("No allowed senders yet. Show a mail's images, then tap Always.")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("0 senders").assertIsDisplayed()
    }

    @Test
    fun websiteIconsAndDuckDuckGoStartOffAndDuckDuckGoShowsOnlyWithIcons() {
        openPrivacy()
        compose.onNode(hasText("Ask DuckDuckGo") and isToggleable()).assertDoesNotExist()
        // The row's info button sits mid-row, so the switch is flipped by its action, not a tap at the center.
        compose.onNode(hasText("Website icons") and isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(behavior.behavior.websiteIcons)
        compose
            .onNode(hasText("Ask DuckDuckGo") and isToggleable())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(behavior.behavior.duckDuckGoIcons)
    }
}
