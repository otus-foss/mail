package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.otus.mail.app.App
import com.otus.mail.core.avatar.IconSource
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
    fun knownPeopleIsTheDefaultAndDoneSavesAChoice() {
        openPrivacy()
        compose.screenshot("2026-10-19-feat-read-view-privacy")
        compose.onNodeWithText("Load images").performClick()
        compose.onNode(hasText("In your contacts") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Never") and isSelectable()).performClick()
        assertEquals("Nothing is saved before Done", RemoteImages.Known, behavior.behavior.remoteImages)
        compose.onNodeWithText("Done").performClick()
        // Done saves once the sheet has finished closing.
        compose.waitForIdle()
        assertEquals(RemoteImages.Never, behavior.behavior.remoteImages)
        compose.onNodeWithText("Never").assertIsDisplayed()
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
    fun iconSourcesShowOnlyWithWebsiteIconsAndBulwarkAsksForItsAddress() {
        openPrivacy()
        compose.onNodeWithText("Icons from").assertDoesNotExist()
        // The row's info button sits mid-row, so the switch is flipped by its action, not a tap at the center.
        compose.onNode(hasText("Website icons") and isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(behavior.behavior.websiteIcons)
        compose.onNodeWithText("Icons from").performScrollTo().performClick()
        compose.onNodeWithText("Bulwark").performClick()
        assertEquals("Nothing is saved before Done", IconSource.Websites, behavior.behavior.iconSource)
        compose.onNodeWithContentDescription("Bulwark address").performTextInput("mail.example.net")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(IconSource.Bulwark, behavior.behavior.iconSource)
        assertEquals("mail.example.net", behavior.behavior.bulwarkAddress)
        compose.onNodeWithText("No Bulwark address, so no icons load").assertDoesNotExist()
    }
}
