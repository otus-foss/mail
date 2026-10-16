package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Adding, editing, and deleting a folder's rules, and starting one from an open message. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderRulesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun start() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = MailBehaviorState()) }
        }
        // Suggestions come from loaded mail, and the Rules row from the loaded script.
        waitFor("Welcome")
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
    }

    private fun openReceipts() {
        start()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Folders").performClick()
        val row = hasText("Receipts") and hasClickAction() and !isSelectable()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(row).performClick()
        waitFor("Add rule")
    }

    /** Starts with the old "Otus Mail" script holding one rule for Receipts. */
    private fun seedRule(condition: String) {
        server.script = """# otus-rules-v1 {"rules":[{"id":"1","conditions":[$condition],"folder":"r"}]}"""
        server.scriptActive = true
    }

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT) { count(text) > 0 }

    private fun count(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    private fun chip(value: String) = compose.onNode(hasText(value) and hasClickAction() and !isSelectable())

    private fun savedScript(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/set" in it } }
        compose.waitForIdle()
        return server.script.orEmpty()
    }

    @Test
    fun addingARuleSavesItMovesTheMailAlreadyThereAndShowsTheChip() {
        server.olderMail += "o1"
        openReceipts()
        compose.onNodeWithText("Add rule").performScrollTo().performClick()
        compose.onNodeWithText("Sort into Receipts").assertExists()
        listOf("From", "To", "Subject", "Mailing list").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Add").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("grace")
        compose.onNodeWithText("Anyone at @example.org").assertExists()
        compose.onNodeWithText("Grace Hopper · 1 message").assertExists()
        compose.onNode(hasText("grace@example.org") and isSelectable()).performClick()
        compose.onNode(hasText("grace@example.org") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("New mail that matches goes to Receipts").assertExists()
        compose.onNode(hasText("From") and hasText("grace@example.org") and !isSelectable()).assertExists()

        waitFor("Move the 1 that are already here")
        compose.onNode(hasText("From Inbox and Archive") and isToggleable()).performScrollTo().assertIsOn()
        compose.onNodeWithText("Add").assertIsEnabled().performClick()

        assertTrue(savedScript(), "grace@example.org" in server.script.orEmpty())
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it && "\"mailboxIds/r\":true" in it } }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Sort into Receipts")).fetchSemanticsNodes().isEmpty() }
        chip("grace@example.org").assertExists()
    }

    @Test
    fun aChipOpensTheSheetToChangeOrDeleteItsRule() {
        seedRule("""{"field":"from","operator":"is","value":"grace@example.org"}""")
        openReceipts()
        chip("grace@example.org").performClick()
        compose.onNodeWithText("Save").assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("@example.org")
        compose.onNode(hasText("Anyone at @example.org") and isSelectable()).performClick()
        compose.onNodeWithText("Save").performClick()

        val changed = savedScript()
        assertTrue(changed, "example.org" in changed)
        assertFalse(changed, "grace@example.org" in changed)
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("@example.org") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }

        server.calls.clear()
        chip("@example.org").performClick()
        compose.onNodeWithText("Delete rule").performClick()
        assertFalse(savedScript(), "example.org" in server.script.orEmpty())
        compose.waitUntil(TIMEOUT) { count("@example.org") == 0 }
    }

    @Test
    fun aRuleTheSheetCannotShowOffersOnlyDelete() {
        seedRule("""{"field":"subject","operator":"startsWith","value":"[Acme]"}""")
        openReceipts()
        waitFor("[Acme]")
        compose.onNode(hasText("starts with “[Acme]”", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("Can't edit this rule here").assertExists()
        compose.onNodeWithText("Delete rule").performClick()
        assertFalse(savedScript(), "[Acme]" in server.script.orEmpty())
    }

    @Test
    fun ruleForSenderPicksAFolderAndOpensItsSheet() {
        start()
        compose.onNodeWithText("Welcome").performClick()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Rule for sender").performClick()
        compose.onNodeWithText("Sort mail from Grace Hopper into").assertExists()
        compose.onNode(hasText("Receipts") and isSelectable()).performClick()
        compose.onNodeWithText("Sort into Receipts").assertExists()
        compose.onNode(hasText("grace@example.org") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Add").performClick()
        assertTrue(savedScript(), "grace@example.org" in server.script.orEmpty())
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
