package com.otus.mail

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.BarButton.MoveTo
import com.otus.mail.feature.inbox.domain.MailAction
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
import org.robolectric.shadows.ShadowDialog

/** Move to buttons: picked for slots in the bar editor, each with its own folder, and moving the open message. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoveToButtonsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer().apply { folders += """{"id": "t", "name": "Travel"}""" }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    /** The topmost match, such as a button in the editor's preview over the page's cards. */
    private fun top(label: String) = compose.onAllNodes(hasContentDescription(label) and hasClickAction()).onLast()

    private fun pick(text: String) =
        compose
            .onAllNodesWithText(text)
            .onLast()
            .performScrollTo()
            .performClick()

    private fun done() = compose.onAllNodesWithText("Done").onLast().performClick()

    @Test
    fun twoMoveToButtonsKeepTheirOwnFoldersAndOneMovesTheMessage() {
        compose.showInbox(server, MailBehaviorState(AndroidMailBehaviorStore(compose.activity)))
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNode(hasText("Action bar") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Open message").performScrollTo().performClick()

        top("Archive").performClick()
        pick("Move to folder…")
        pick("Receipts")
        done()
        top("Delete").performClick()
        pick("Move to folder…")
        // A folder another slot moves to would trade places with it.
        compose.onNodeWithText("Swaps with Button 1").assertExists()
        pick("Travel")
        done()
        // The first slot's picker switches its folder without removing the button.
        top("Move to Receipts").performClick()
        compose.onNodeWithContentDescription("Receipts, change").performClick()
        pick("Receipts / Amazon")
        done()
        screenshot("edit-sheet")
        done()

        val expected =
            ActionBarLayout(
                listOf(MoveTo(listOf("Receipts", "Amazon")), MoveTo(listOf("Travel")), MailAction.Move),
                MailAction.Reply,
            )
        assertEquals(expected, AndroidMailBehaviorStore(compose.activity).read().savedLayout(ActionBarPlace.Message))

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isSelectable()).performClick()
        compose.onNodeWithText("Welcome").performClick()
        button("Move to Travel").performClick()
        assertTrue(moveUpdate(), "\"mailboxIds/t\":true" in moveUpdate())
    }

    @Test
    fun theMainButtonNamesItsFolderAndMovesTheMessage() {
        val bar = ActionBarLayout(listOf(MailAction.Archive, MoveTo(listOf("receipts"))), MoveTo(listOf("Travel")))
        val behavior = MailBehavior(markReadOnOpen = false, actionBar = mapOf(ActionBarPlace.Message to bar))
        compose.showInbox(server, MailBehaviorState(initial = behavior))
        compose.onNodeWithText("Welcome").performClick()
        compose.waitForNode(hasContentDescription("Travel") and hasClickAction())
        button("Move to Receipts").assertExists()
        screenshot("message-bar")
        button("Travel").performClick()
        assertTrue(moveUpdate(), "\"mailboxIds/a\":null,\"mailboxIds/t\":true" in moveUpdate())
    }

    private fun moveUpdate(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "mailboxIds" in it } }
        return server.calls.first { "Email/set" in it && "mailboxIds" in it }
    }

    /** Saves the sheet on top, or the page without one, to build/reports/action-targets for review. */
    /** The open sheet, or the page when none is open. */
    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val sheet = ShadowDialog.getShownDialogs().lastOrNull { it.isShowing }
                (sheet?.window?.decorView ?: compose.activity.window.decorView).drawnBitmap()
            }
        bitmap.savePng("build/reports/action-targets/$name.png")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
