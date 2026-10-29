package com.otus.mail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Move to sheet of an open message and of a selection, against a server that answers like Stalwart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoveSheetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openMoveSheet() {
        compose.showInbox(server)
        compose.onNodeWithText("Welcome").performClick()
        compose.onNodeWithContentDescription("Move").performClick()
        compose.waitForText("Move to")
    }

    private fun moveUpdate(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "mailboxIds" in it } }
        return server.calls.first { "Email/set" in it && "mailboxIds" in it }
    }

    @Test
    fun aSystemFolderPillMovesTheMessageAndClosesIt() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk"}"""
        openMoveSheet()
        compose.onNode(hasText("Grace Hopper · ", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Spam").performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/j\":true}}" in move)
        compose.onNodeWithText("Move to").assertDoesNotExist()
        // The message page closes with the sheet, back to the list.
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun theSelectionBarMovesTheSelectionThroughTheSameSheet() {
        compose.showInbox(server)
        compose.onNodeWithText("Welcome").performTouchInput { longClick() }
        compose.onNode(hasContentDescription("Move, 1 message") and hasClickAction()).performClick()
        compose.waitForText("Move to")
        compose.onNodeWithText("Amazon").performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)
        compose.onNode(hasContentDescription("Move, 1 message")).assertDoesNotExist()
    }

    @Test
    fun theFolderTheSendersMailUsuallyGoesToIsSuggested() {
        server.senderFolders += listOf("z", "a", "z", "r")
        openMoveSheet()
        val suggestion = "Move to Receipts / Amazon, where Grace Hopper's mail usually goes"
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodesWithContentDescription(suggestion).fetchSemanticsNodes().isNotEmpty() }
        screenshot("move-sheet")
        compose.onNodeWithContentDescription(suggestion).performClick()
        val move = moveUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)
    }

    @Test
    fun theFolderTheMessageIsInIsMarkedAndDoesNotMoveIt() {
        compose.showInbox(server)
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Receipts") and isSelectable() and !hasText("Welcome")).performClick()
        compose.waitForText("Order shipped")
        compose.onNodeWithText("Order shipped").performClick()
        compose.onNodeWithContentDescription("Move").performClick()
        val current = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Current folder")
        compose.waitForNode(current)
        screenshot("move-sheet-current-folder")
        compose.onNode(current).assertIsDisplayed()
        compose.onNodeWithText("Amazon").assertIsDisplayed()
    }

    @Test
    fun aTopLevelSuggestionDoesNotRepeatItsName() {
        server.senderFolders += listOf("r", "r")
        openMoveSheet()
        val suggestion = "Move to Receipts, where Grace Hopper's mail usually goes"
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodesWithContentDescription(suggestion).fetchSemanticsNodes().isNotEmpty() }
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
        compose.waitForText("Moved to Amazon")
    }

    @Test
    fun sortingFutureMailAlsoAddsARuleForTheChosenMail() {
        openMoveSheet()
        compose.onNodeWithText("This message and future ones like it go there.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Also sort future mail").performClick()
        screenshot("move-sheet-sorting")
        compose.onNodeWithContentDescription("From grace@example.org, change").performClick()
        compose.onNodeWithText("Sort mail that…").assertIsDisplayed()
        screenshot("move-sheet-conditions")
        compose.onNodeWithText("Anyone at this company").performClick()
        // The condition sheet opened last, so its Done is the last one.
        compose.onAllNodesWithText("Done").onLast().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithText("Sort mail that…").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Amazon").performClick()
        moveUpdate()
        compose.waitUntil(TIMEOUT) { server.script != null }
        val script = server.script.orEmpty()
        assertTrue(script, "address :domain :is \"from\" \"example.org\"" in script)
        assertTrue(script, "\"z\"" in script)
    }

    @Test
    fun aSavedRuleOffersToMoveOlderMailItMatches() {
        server.olderMail += listOf("o1", "o2")
        moveAndSort()
        compose.waitForText("2 older messages match")
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
        compose.waitForText("No older mail in Inbox or Archive matches.")
        compose.onNodeWithText("Undo").performClick()
        val back = "\"update\":{\"m1\":{\"mailboxIds/z\":null,\"mailboxIds/a\":true}}"
        compose.waitUntil(TIMEOUT) { server.calls.any { back in it } }
        compose.waitUntil(TIMEOUT) { "\"z\"" !in server.script.orEmpty() }
        compose.onNodeWithText("Moved to Amazon").assertDoesNotExist()
    }

    /** Saves the sheet on top to build/reports/inbox for review. */
    private fun screenshot(name: String) = compose.dialogBitmap().savePng("build/reports/inbox/$name.png")

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
