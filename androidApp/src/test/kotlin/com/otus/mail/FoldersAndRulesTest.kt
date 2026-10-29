package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.otus.mail.app.App
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

/** Folders and rules against a server that keeps what the app changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldersAndRulesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun start(behavior: MailBehavior = MailBehavior()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(), behaviorState = MailBehaviorState(initial = behavior))
            }
        }
    }

    private fun openSettingsPage(title: String) {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText(title).performClick()
    }

    @Test
    fun sidebarNestsFoldersAndOpensOneAsAPage() {
        server.folders += """{"id": "y", "name": "Books", "parentId": "r", "unreadEmails": 4}"""
        start()
        compose.waitForText("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForNode(hasText("Amazon") and isSelectable())
        compose.onNode(hasText("Inbox") and hasText("1") and isSelectable()).assertIsDisplayed()
        // A collapsed folder hides its subfolders and counts their mail with its own.
        compose.onNodeWithContentDescription("Collapse Receipts").performClick()
        compose.onNode(hasText("Amazon") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasText("Receipts") and hasText("6") and isSelectable()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand Receipts").performClick()
        compose.onNode(hasText("Receipts") and hasText("2") and isSelectable()).performClick()
        compose.onNodeWithText("Search Receipts").assertIsDisplayed()
        compose.waitForText("Order shipped")
        assertTrue(server.calls.any { "\"inMailbox\":\"r\"" in it })
    }

    @Test
    fun rowsNameTheOtherFoldersAMessageIsIn() {
        server.alsoIn += "r"
        start()
        compose.waitForText("Welcome")
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(hasText("Welcome") and hasContentDescription("Folders: Receipts", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        server.alsoIn.clear()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        // Message rows are selectable too, so this picks the sidebar's folder.
        compose.onNode(hasText("Receipts") and isSelectable() and !hasText("Welcome")).performClick()
        compose.waitForText("Order shipped")
        compose.onNodeWithText("Order shipped").assertIsDisplayed()
    }

    @Test
    fun aNewFolderIsCreatedOnTheServerAndListed() {
        start()
        openSettingsPage("Folders & rules")
        compose.waitForText("Receipts")
        compose.onNode(hasContentDescription("New folder") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Name").performTextInput("Travel")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(
                    hasText("Travel") and hasClickAction() and !hasSetTextAction(),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(server.calls.any { "\"create\":{\"folder\":{\"name\":\"Travel\"" in it })
    }

    @Test
    fun turningOnReadLaterAddsItsMailboxAfterTheInbox() {
        start()
        openSettingsPage("Mail behavior")
        compose.onNode(hasText("Read later") and isToggleable()).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) {
            server.calls.any { "\"create\":{\"folder\":{\"name\":\"Read later\",\"parentId\":null" in it }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val tile = hasContentDescription("Read later")
        compose.waitForNode(tile)
        val inbox = compose.onNode(hasText("Inbox") and isSelectable()).fetchSemanticsNode().boundsInRoot
        val readLater = compose.onNode(tile).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNode(hasText("Receipts") and isSelectable()).fetchSemanticsNode().boundsInRoot
        assertTrue(inbox.bottom <= readLater.top && readLater.bottom <= receipts.top)
        // Read later is a mailbox, not one of the user's folders.
        compose.onNode(hasText("Read later") and isSelectable()).assertDoesNotExist()
    }

    @Test
    fun readLaterSubfoldersJoinTheUsersFolders() {
        server.folders += """{"id": "l", "name": "Read later"}"""
        server.folders += """{"id": "x", "name": "Articles", "parentId": "l"}"""
        start(MailBehavior(readLater = true))
        compose.waitForText("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val articles = hasText("Articles") and isSelectable()
        compose.waitForNode(articles)
        val folder = compose.onNodeWithText("Articles", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNodeWithText("Receipts", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(receipts.left, folder.left)
    }

    @Test
    fun mailboxesListSpamAndTrashOnceAndOpenThem() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk", "unreadEmails": 3}"""
        server.folders += """{"id": "t", "name": "Deleted Items", "role": "trash"}"""
        start()
        compose.waitForText("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val spam = hasContentDescription("Spam, 3")
        compose.waitForNode(spam)
        // The app names each role in place of the server's names, above the user's own folders.
        assertTrue(compose.onAllNodes(hasText("Junk Mail")).fetchSemanticsNodes().isEmpty())
        val trash = compose.onNode(hasContentDescription("Trash")).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNode(hasText("Receipts") and isSelectable()).fetchSemanticsNode().boundsInRoot
        assertTrue(trash.bottom <= receipts.top)
        compose.onNode(spam).performClick()
        compose.onNodeWithText("Search Spam").assertIsDisplayed()
        compose.waitForText("Welcome")
        assertTrue(server.calls.any { "\"inMailbox\":\"j\"" in it })
    }

    @Test
    fun settingsFoldersShowEachFoldersFirstRule() {
        server.script =
            "# otus-rules-v1 " +
            """{"rules":[{"id":"1","conditions":[{"field":"from","operator":"is","value":"grace@example.org"}],""" +
            """"folder":"r"}]}"""
        server.scriptActive = true
        start()
        openSettingsPage("Folders & rules")
        compose.waitForText("grace@example.org")
        compose.onNodeWithText("grace@example.org", substring = true).assertIsDisplayed()
    }

    private fun openWelcome() {
        start()
        compose.waitForText("Welcome")
        // Both actions appear once folders and rules have loaded.
        compose.waitForCall(server, "SieveScript/get")
        compose.waitForIdle()
        compose.onNodeWithText("Welcome").performClick()
    }

    @Test
    fun anOpenMessageMovesToAFolder() {
        openWelcome()
        compose.onNodeWithContentDescription("Move").performClick()
        compose.onNodeWithText("Amazon").performClick()
        // Opening marked the message read; the move is the update that changes its folders.
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "mailboxIds" in it } }
        val move = server.calls.first { "Email/set" in it && "mailboxIds" in it }
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
