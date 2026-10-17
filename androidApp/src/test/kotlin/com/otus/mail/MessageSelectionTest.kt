package com.otus.mail

import androidx.activity.compose.setContent
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
import com.otus.mail.app.App
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
    private fun selectWelcome() {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(
                    services = server.services(),
                    behaviorState = MailBehaviorState(initial = MailBehavior(markReadOnOpen = false)),
                )
            }
        }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Welcome")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
        compose.waitForIdle()
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
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it } }
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

    private companion object {
        const val TIMEOUT = 5_000L
        const val SHELL = "Move, 1 message"
    }
}
