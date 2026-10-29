package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Selecting inbox rows with a long press, and the selection's bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private val selectionBar get() = compose.onNode(hasContentDescription(SHELL) and hasClickAction())

    /** Opens the inbox once folders and rules have loaded, then long-presses the one message. */
    private fun selectWelcome(behavior: MailBehavior = MailBehavior(markReadOnOpen = false)) {
        compose.showInbox(server, MailBehaviorState(initial = behavior))
        compose.onNodeWithText("Welcome").performTouchInput { longClick() }
    }

    private fun assertNothingSelected() {
        selectionBar.assertDoesNotExist()
        // Outside selection the row reports no selected state at all.
        compose.onNodeWithText("Welcome").assertIsDisplayed()
        compose.onNode(hasText("Welcome") and isSelected()).assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun aLongPressSelectsTheRowAndShowsTheSelectionBar() {
        selectWelcome()
        compose.onNode(hasText("Welcome") and isSelected()).assertIsDisplayed()
        selectionBar.assertIsDisplayed()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun trashFilesTheSelectionAndEndsIt() {
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        selectWelcome()
        compose.onNode(hasContentDescription("Delete") and hasClickAction()).performClick()
        compose.waitForCall(server, "Email/set")
        val trash = server.calls.last { "Email/set" in it }
        assertTrue(trash, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/t\":true}}" in trash)
        selectionBar.assertDoesNotExist()
    }

    @Test
    fun systemBackClearsTheSelection() {
        selectWelcome()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertNothingSelected()
    }

    @Test
    fun aTapOnTheOnlySelectedRowClearsTheSelectionWithoutOpeningIt() {
        selectWelcome()
        compose.onNodeWithText("Welcome").performClick()
        assertNothingSelected()
    }

    @Test
    fun aMinimizedBarListsTheActionsItLeavesOutInAMenu() {
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        selectWelcome(MailBehavior(markReadOnOpen = false, minimizedBar = true))
        compose.onNode(hasContentDescription("Delete") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitForCall(server, "Email/set")
        assertTrue(server.calls.last { "Email/set" in it }.contains("\"mailboxIds/t\":true"))
        selectionBar.assertDoesNotExist()
    }

    private companion object {
        const val SHELL = "Move, 1 message"
    }
}
