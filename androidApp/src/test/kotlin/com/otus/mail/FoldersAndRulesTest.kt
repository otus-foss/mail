package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.otus.mail.app.App
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

    private fun start() {
        compose.runOnUiThread { compose.activity.setContent { App(services = server.services()) } }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openSettingsPage(title: String) {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Settings") and isSelectable()).performClick()
        compose.onNodeWithText(title).performClick()
    }

    @Test
    fun sidebarNestsFoldersAndOpensOneAsAPage() {
        start()
        waitFor("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Amazon") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        // One account: its inbox is the unified Inbox, so it is listed once.
        compose.onNode(hasText("Inbox") and isSelectable()).assertIsDisplayed()
        compose.onNode(hasText("Receipts") and hasText("2") and isSelectable()).performClick()
        compose.onNodeWithText("Search Receipts").assertIsDisplayed()
        waitFor("Order shipped")
        assertTrue(server.calls.any { "\"inMailbox\":\"r\"" in it })
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
            compose.onAllNodes(hasText("Travel") and hasText("0 messages")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(server.calls.any { "\"create\":{\"folder\":{\"name\":\"Travel\"" in it })
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
    fun anOpenMessageMovesToAFolderOrStartsARuleForItsSender() {
        start()
        waitFor("Welcome")
        compose.onNodeWithText("Welcome").performClick()
        // Both actions appear once folders and rules have loaded.
        waitFor("Rule for sender")
        compose.onNodeWithText("Move").performClick()
        compose.onNodeWithText("Receipts / Amazon").performClick()
        // Opening marked the message read; the move is the update that changes its folders.
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "mailboxIds" in it } }
        val move = server.calls.first { "Email/set" in it && "mailboxIds" in it }
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/z\":true}}" in move)

        // The moved row leaves at once; this server still lists it, so the reload brings it back.
        waitFor("Welcome")
        compose.onNodeWithText("Welcome").performClick()
        compose.onNodeWithText("Rule for sender").performClick()
        compose.onNodeWithText("New rule").assertIsDisplayed()
        compose.onNodeWithText("grace@example.org").assertIsDisplayed()
        compose.onNodeWithText("is").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
