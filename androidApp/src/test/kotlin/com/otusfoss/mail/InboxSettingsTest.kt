package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.mail.app.App
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mail behavior › Inbox shows, a sheet that chooses whether Spam and Trash join the Inbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()

    private fun openInboxSettings() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNodeWithText("Inbox shows").performClick()
    }

    private fun toggle(title: String) = compose.onNode(hasText(title) and isToggleable())

    @Test
    fun spamAndTrashJoinTheInboxAndTheRowSaysSo() {
        openInboxSettings()
        toggle("Spam").assertIsOff().performClick()
        toggle("Trash").performClick()
        assertEquals(InboxContents(spam = true, trash = true), behavior.behavior.list.inbox)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Inbox + Spam + Trash").assertIsDisplayed()
    }

    @Test
    fun theInboxSheetLeavesAddressesToTheSidebar() {
        openInboxSettings()
        toggle("Every address").assertDoesNotExist()
        toggle("Spam").assertIsDisplayed()
    }
}
