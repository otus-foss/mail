package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.InboxPill.Filter.Attachments
import com.otus.mail.feature.inbox.domain.InboxPill.Filter.Flagged
import com.otus.mail.feature.inbox.domain.InboxPill.Filter.Unread
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pills in the inbox row, chosen in Settings and filled from the server's folders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxPillsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()

    private fun start(initial: MailBehavior = MailBehavior()) {
        behavior.update { initial }
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = behavior) }
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pill(text: String) = compose.onNode(hasText(text) and isToggleable())

    @Test
    fun theReadLaterPillShowsSavedMailInPlaceOfTheInbox() {
        server.folders += """{"id": "l", "name": "Read later"}"""
        server.subjects["l"] = "Saved article"
        start(MailBehavior().withReadLater(true))
        waitFor("Welcome")
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Read later") and isToggleable()).fetchSemanticsNodes().isNotEmpty()
        }
        pill("Read later").performClick()
        waitFor("Saved article")
        pill("Read later").assertIsOn()
        compose.onNodeWithText("Search Read later").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Welcome")).fetchSemanticsNodes().isEmpty())
        assertTrue(server.calls.any { "\"inMailbox\":\"l\"" in it })
        // Pressing it again returns to the inbox.
        pill("Read later").performClick()
        waitFor("Welcome")
    }

    private fun openPillsSheet() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNodeWithText("Pills").performScrollTo().performClick()
        waitFor("Shown")
    }

    @Test
    fun draggingAHandleMovesAPillAndDoneSavesTheOrder() {
        start(MailBehavior(pills = listOf(Unread, Attachments, Flagged)))
        openPillsSheet()
        // Far past the last row: the row stops at the end of the list.
        compose.onNodeWithContentDescription("Reorder Unread", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, height * 20f), durationMillis = 600)
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(listOf(Attachments, Flagged, Unread), behavior.behavior.pills) }
    }

    @Test
    fun accessibilityServicesMoveAPillWithoutDragging() {
        start(MailBehavior(pills = listOf(Unread, Attachments)))
        openPillsSheet()
        val attachments = compose.onNode(hasText("Attachments") and isToggleable()).fetchSemanticsNode()
        val moveUp = attachments.config[SemanticsActions.CustomActions].single { it.label == "Move up" }
        compose.runOnUiThread { moveUp.action() }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(listOf(Attachments, Unread), behavior.behavior.pills) }
    }

    @Test
    fun aFolderChosenInSettingsJoinsTheRow() {
        start()
        openPillsSheet()
        // Nested folders list by name under their parent.
        waitFor("Amazon")
        compose.onNode(hasText("Receipts / Amazon") and isToggleable()).assertDoesNotExist()
        compose.onNode(hasText("Receipts") and isToggleable()).performClick()
        // Without its parent in the list, a folder names the parent again.
        compose.onNode(hasText("Receipts / Amazon") and isToggleable()).assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("3 shown").assertIsDisplayed()
        assertEquals(
            InboxPill.defaults(readLater = false) + InboxPill.Folder("ada", "r"),
            behavior.behavior.pills,
        )
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isSelectable()).performClick()
        waitFor("Welcome")
        pill("Receipts").performClick()
        waitFor("Order shipped")
        compose.onNodeWithText("Search Receipts").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
