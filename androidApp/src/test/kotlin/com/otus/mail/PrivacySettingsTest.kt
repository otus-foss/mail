package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.otus.avatar.IconSource
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

/** Settings › Privacy & security: when remote images load, the allowed senders, and website icons. */
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
        compose.onNodeWithText("Remote images, avatars, and this device").assertIsDisplayed()
        compose.onNodeWithText("Privacy & security").performClick()
        compose.onNodeWithText("Load remote images").assertIsDisplayed()
    }

    @Test
    fun knownPeopleIsTheDefaultAndAChoiceAppliesAtOnce() {
        openPrivacy()
        compose.screenshot("2026-10-19-feat-read-view-privacy")
        compose.onNodeWithText("Load images").performClick()
        compose.onNode(hasText("In your contacts") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Never") and isSelectable()).performClick()
        // Like every other choice sheet in settings, picking applies at once and moves the check.
        assertEquals(RemoteImages.Never, behavior.behavior.remoteImages)
        compose.onNode(hasText("Never") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Never").assertIsDisplayed()
        // Allowed senders only count while images load for contacts, which their row now says.
        compose.onNodeWithText("2 senders, used only with In your contacts").assertIsDisplayed()
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
    fun iconSourcesWaitForWebsiteIconsAndBulwarkAsksForItsAddress() {
        openPrivacy()
        // The row stays in place while icons are off, disabled with the reason below it.
        compose.onNodeWithText("Icons from").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Turn on Website icons to choose").assertIsDisplayed()
        // The row's info button sits mid-row, so the switch is flipped by its action, not a tap at the center.
        compose.onNode(hasText("Website icons") and isToggleable()).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(behavior.behavior.websiteIcons)
        compose
            .onNodeWithText("Icons from")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.onNodeWithText("Bulwark").performClick()
        assertEquals("Nothing is saved before Done", IconSource.Websites, behavior.behavior.iconSource)
        compose.onNodeWithContentDescription("Bulwark address").performTextInput("mail.example.net")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(IconSource.Bulwark, behavior.behavior.iconSource)
        assertEquals("mail.example.net", behavior.behavior.bulwarkAddress)
        compose.onNodeWithText("No Bulwark address, so no icons load").assertDoesNotExist()
    }

    @Test
    fun doneOnAnotherSourceKeepsTheSavedBulwarkAddress() {
        behavior.update {
            it.copy(websiteIcons = true, iconSource = IconSource.Bulwark, bulwarkAddress = "mail.example.net")
        }
        openPrivacy()
        compose.onNodeWithText("Icons from").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Bulwark address").performTextClearance()
        compose.onNodeWithText("DuckDuckGo").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(IconSource.DuckDuckGo, behavior.behavior.iconSource)
        assertEquals("mail.example.net", behavior.behavior.bulwarkAddress)
    }
}
