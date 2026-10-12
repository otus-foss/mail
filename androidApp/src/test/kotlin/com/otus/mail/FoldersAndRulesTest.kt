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

    private fun waitFor(text: String) {
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
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
        waitFor("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Amazon") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Inbox") and hasText("1") and isToggleable()).assertIsDisplayed()
        // A collapsed folder hides its subfolders and counts their mail with its own.
        compose.onNodeWithContentDescription("Collapse Receipts").performClick()
        compose.onNode(hasText("Amazon") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasText("Receipts") and hasText("6") and isSelectable()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand Receipts").performClick()
        compose.onNode(hasText("Receipts") and hasText("2") and isSelectable()).performClick()
        compose.onNodeWithText("Search Receipts").assertIsDisplayed()
        waitFor("Order shipped")
        assertTrue(server.calls.any { "\"inMailbox\":\"r\"" in it })
    }

    @Test
    fun rowsNameTheOtherFoldersAMessageIsIn() {
        server.alsoIn += "r"
        start()
        waitFor("Welcome")
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Welcome") and hasText("Receipts")).fetchSemanticsNodes().isNotEmpty()
        }
        // Without labels or files, the row shows the start of the message instead.
        server.alsoIn.clear()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        // Message rows are selectable too, so this picks the sidebar's folder.
        compose.onNode(hasText("Receipts") and isSelectable() and !hasText("Welcome")).performClick()
        waitFor("Order shipped")
        compose.onNode(hasText("Order shipped") and hasText("Hello")).assertIsDisplayed()
    }

    @Test
    fun aNewFolderIsCreatedOnTheServerAndListed() {
        start()
        openSettingsPage("Folders")
        waitFor("Receipts")
        compose.onNodeWithText("New folder").performScrollTo().performClick()
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
        openSettingsPage("Appearance & behavior")
        compose.onNodeWithText("Read later").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) {
            server.calls.any { "\"create\":{\"folder\":{\"name\":\"Read later\",\"parentId\":null" in it }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val pill = hasText("Read later") and isToggleable()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(pill).fetchSemanticsNodes().isNotEmpty() }
        val inbox = compose.onNode(hasText("Inbox") and isToggleable()).fetchSemanticsNode().boundsInRoot
        val readLater = compose.onNode(pill).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNode(hasText("Receipts") and isSelectable()).fetchSemanticsNode().boundsInRoot
        assertTrue(inbox.right <= readLater.left && readLater.bottom <= receipts.top)
        // Read later is a mailbox, not one of the user's folders.
        compose.onNode(hasText("Read later") and isSelectable()).assertDoesNotExist()
    }

    @Test
    fun readLaterSubfoldersJoinTheUsersFolders() {
        server.folders += """{"id": "l", "name": "Read later"}"""
        server.folders += """{"id": "x", "name": "Articles", "parentId": "l"}"""
        start(MailBehavior(readLater = true))
        waitFor("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val articles = hasText("Articles") and isSelectable()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(articles).fetchSemanticsNodes().isNotEmpty() }
        val folder = compose.onNodeWithText("Articles", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNodeWithText("Receipts", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(receipts.left, folder.left)
    }

    @Test
    fun mailboxesListSpamAndTrashOnceAndOpenThem() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk", "unreadEmails": 3}"""
        server.folders += """{"id": "t", "name": "Deleted Items", "role": "trash"}"""
        start()
        waitFor("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val spam = hasText("Spam") and hasText("3") and isToggleable()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(spam).fetchSemanticsNodes().isNotEmpty() }
        // The app names each role in place of the server's names, above the user's own folders.
        assertTrue(compose.onAllNodes(hasText("Junk Mail")).fetchSemanticsNodes().isEmpty())
        val trash = compose.onNode(hasText("Trash") and isToggleable()).fetchSemanticsNode().boundsInRoot
        val receipts = compose.onNode(hasText("Receipts") and isSelectable()).fetchSemanticsNode().boundsInRoot
        assertTrue(trash.bottom <= receipts.top)
        compose.onNode(spam).performClick()
        compose.onNodeWithText("Search Spam").assertIsDisplayed()
        waitFor("Welcome")
        assertTrue(server.calls.any { "\"inMailbox\":\"j\"" in it })
    }

    @Test
    fun aRuleIsSavedAsAnActiveSieveScript() {
        start()
        openSettingsPage("Rules")
        waitFor("New rule")
        compose.onNodeWithText("New rule").performClick()
        compose.onNodeWithContentDescription("Condition 1 text").performTextInput("amazon")
        compose.onNodeWithText("Choose a folder").performScrollTo().performClick()
        compose.onNodeWithText("Receipts / Amazon").performClick()
        compose.onNodeWithText("Save rule").performScrollTo().performClick()
        waitFor("Rules are on")
        val script = server.script.orEmpty()
        assertTrue(script, "header :contains \"from\" \"amazon\"" in script)
        assertTrue(script, "fileinto :mailboxid \"z\" \"INBOX\";" in script)
        assertTrue("The first rule turns the rules on", server.scriptActive)
        compose.onNodeWithText("From contains “amazon”").assertIsDisplayed()
    }

    @Test
    fun settingsFoldersShowTheSendersRulesMoveThere() {
        server.script =
            "# otus-rules-v1 " +
            """{"rules":[{"id":"1","conditions":[{"field":"from","operator":"is","value":"grace@example.org"}],""" +
            """"folder":"r"}]}"""
        server.scriptActive = true
        start()
        // Grace's name comes from her message in the inbox.
        waitFor("Welcome")
        openSettingsPage("Folders")
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(hasContentDescription("Rules move mail from Grace Hopper here"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Grace").assertIsDisplayed()
        compose.onNodeWithText("Ada").assertIsDisplayed()
    }

    private fun openWelcome() {
        start()
        waitFor("Welcome")
        // Both actions appear once folders and rules have loaded.
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
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

    @Test
    fun anOpenMessageStartsARuleForItsSender() {
        openWelcome()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Rule for sender").performClick()
        compose.onNodeWithText("New rule").assertIsDisplayed()
        compose.onNodeWithText("grace@example.org").assertIsDisplayed()
        compose.onNodeWithText("is").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
