package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** The Move to sheet of an open message and of a selection, against a server that answers like Stalwart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoveSheetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openMoveSheet() {
        showInbox()
        compose.onNodeWithText("Welcome").performClick()
        compose.onNodeWithContentDescription("Move").performClick()
        waitFor("Move to")
    }

    /** The inbox once folders and rules have loaded, so folder actions are offered. */
    private fun showInbox() {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(
                    services = server.services(),
                    behaviorState = MailBehaviorState(initial = MailBehavior(markReadOnOpen = false)),
                )
            }
        }
        waitFor("Welcome")
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
        compose.waitForIdle()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun moveUpdate(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "mailboxIds" in it } }
        return server.calls.first { "Email/set" in it && "mailboxIds" in it }
    }

    @Test
    fun aSystemFolderPillMovesTheMessageAndClosesIt() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk"}"""
        openMoveSheet()
        compose.onNodeWithText("Grace Hopper · grace@example.org").assertIsDisplayed()
        compose.onNodeWithText("Spam").performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/j\":true}}" in move)
        compose.onNodeWithText("Move to").assertDoesNotExist()
        // The message page closes with the sheet, back to the list.
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun theSelectionBarMovesTheSelectionThroughTheSameSheet() {
        showInbox()
        compose.onNodeWithText("Welcome").performTouchInput { longClick() }
        compose.onNode(hasContentDescription("Move, 1 message") and hasClickAction()).performClick()
        waitFor("Move to")
        compose.onNodeWithText("Amazon").performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)
        compose.onNode(hasContentDescription("Move, 1 message")).assertDoesNotExist()
    }

    @Test
    fun theFolderTheSendersMailUsuallyGoesToIsSuggested() {
        server.senderFolders += listOf("z", "a", "z", "r")
        openMoveSheet()
        val suggestion = "Receipts / Amazon · where Grace Hopper usually goes"
        waitFor(suggestion)
        screenshot("move-sheet")
        compose.onNodeWithText(suggestion).performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)
    }

    @Test
    fun aTopLevelSuggestionDoesNotRepeatItsName() {
        server.senderFolders += listOf("r", "r")
        openMoveSheet()
        waitFor("Where Grace Hopper usually goes")
        compose.onNodeWithText("Receipts · where Grace Hopper usually goes").assertDoesNotExist()
    }

    @Test
    fun aFolderCollapsesItsNestedFoldersUntilSearchShowsThem() {
        openMoveSheet()
        compose.onNodeWithContentDescription("Collapse Receipts").performClick()
        compose.onNodeWithText("Amazon").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("amaz")
        compose.onNodeWithText("Amazon").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithContentDescription("Expand Receipts").performClick()
        compose.onNodeWithText("Amazon").assertIsDisplayed()
    }

    @Test
    fun searchNarrowsTheFolders() {
        openMoveSheet()
        compose.onNodeWithText("Receipts").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("amaz")
        compose.onNodeWithText("Receipts").assertDoesNotExist()
        compose.onNodeWithText("Amazon").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("x")
        compose.onNodeWithText("No folders match.").assertIsDisplayed()
    }

    /** Turns on the sort pill and moves the message to Amazon, which adds a rule for its sender. */
    private fun moveAndSort() {
        openMoveSheet()
        compose.onNodeWithContentDescription("Also sort future mail").performClick()
        compose.onNodeWithText("Amazon").performClick()
        waitFor("Moved to Amazon")
    }

    @Test
    fun sortingFutureMailAlsoAddsARuleForTheChosenMail() {
        openMoveSheet()
        compose.onNodeWithText("Mail that").assertDoesNotExist()
        compose.onNodeWithContentDescription("Also sort future mail").performClick()
        screenshot("move-sheet-sorting")
        compose.onNodeWithText("is from grace@example.org").performClick()
        compose.onNodeWithText("Sort mail that…").assertIsDisplayed()
        screenshot("move-sheet-conditions")
        compose.onNodeWithText("Every sender at this company").performClick()
        // The condition sheet opened last, so its Done is the last one.
        compose.onAllNodesWithText("Done").onLast().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Sort mail that…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Amazon").performClick()
        moveUpdate()
        compose.waitUntil(TIMEOUT) { server.script != null }
        val script = server.script.orEmpty()
        assertTrue(script, "\"@example.org\"" in script)
        assertTrue(script, "\"z\"" in script)
    }

    @Test
    fun aSavedRuleOffersToMoveOlderMailItMatches() {
        server.olderMail += listOf("o1", "o2")
        moveAndSort()
        waitFor("2 older messages match")
        compose.onNodeWithText("New mail from grace@example.org will go there too.").assertIsDisplayed()
        screenshot("sorted-move")
        compose.onNodeWithText("Move 2 too").performClick()
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "\"o1\"" in it } }
        val move = server.calls.first { "Email/set" in it && "\"o1\"" in it }
        assertTrue(move, "\"o2\"" in move && "\"mailboxIds/z\":true" in move)
        assertTrue(move, "\"m1\"" !in move)
    }

    @Test
    fun undoMovesTheMessageBackAndDeletesTheRule() {
        moveAndSort()
        waitFor("No older mail in Inbox matches.")
        compose.onNodeWithText("Undo").performClick()
        val back = "\"update\":{\"m1\":{\"mailboxIds/z\":null,\"mailboxIds/a\":true}}"
        compose.waitUntil(TIMEOUT) { server.calls.any { back in it } }
        compose.waitUntil(TIMEOUT) { "\"z\"" !in server.script.orEmpty() }
        compose.onNodeWithText("Moved to Amazon").assertDoesNotExist()
    }

    /** Saves the sheet on top to build/reports/inbox for review. */
    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val view = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val directory = File("build/reports/inbox").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
