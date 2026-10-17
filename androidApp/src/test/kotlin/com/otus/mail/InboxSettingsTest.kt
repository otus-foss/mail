package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Appearance & behavior › Inbox, which chooses what the Inbox lists. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()

    private fun openInboxSettings() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(withBob = true), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        // Folders load after the accounts, whose addresses the page lists.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Receipts") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance & behavior").performClick()
        compose.onNodeWithText("All mail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inbox shows").performClick()
    }

    private fun toggle(title: String) = compose.onNode(hasText(title) and isToggleable())

    @Test
    fun spamAndTrashJoinTheInboxAndTheRowSaysSo() {
        openInboxSettings()
        toggle("Spam").assertIsOff().performClick()
        toggle("Trash").performClick()
        assertEquals(InboxContents(spam = true, trash = true), behavior.behavior.inbox)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("All mail · Spam · Trash").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAddressesKeepsTheOthersUntilEveryAddressIsOnAgain() {
        openInboxSettings()
        toggle("Every address").assertIsOn().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("bob@example.org") and isToggleable()).fetchSemanticsNodes().isNotEmpty()
        }
        toggle("bob@example.org").assertIsOn().performClick()
        val left = behavior.behavior.inbox.addresses
        assertTrue(left.isNotEmpty())
        assertTrue(left.none { it.address == "bob@example.org" })
        toggle("bob@example.org").assertIsOff()
        toggle("Every address").performClick()
        assertEquals(InboxContents(), behavior.behavior.inbox)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
