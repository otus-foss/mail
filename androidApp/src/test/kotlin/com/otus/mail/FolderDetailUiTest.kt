package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.app.App
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.folders.domain.readColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A folder's page from Settings › Folders & rules: its rows, its color, deleting it, and when Rules hides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderDetailUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val store = InMemoryFolderColorStore()

    private fun openFolder(name: String) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(folderColorStore = store), behaviorState = MailBehaviorState())
            }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Folders & rules").performClick()
        val row = hasText(name) and hasClickAction() and !isSelectable()
        compose.waitForNode(row)
        compose.onNode(row).performClick()
        compose.waitForText("Delete folder")
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    @Test
    fun theDetailPageListsItsRowsAndKeepsDeleteOffWhileTheFolderHasMail() {
        openFolder("Receipts")
        compose.onNodeWithText("3 messages · 2 unread · 1 folder inside").assertExists()
        compose.onNode(hasText("Name") and hasText("Receipts") and hasClickAction()).assertExists()
        compose.onNode(hasText("Color") and hasClickAction()).assertExists()
        compose.waitForText("New mail that matches any of these goes here")
        compose.onNodeWithText("Add rule").assertExists()
        compose.onNodeWithText("Empty its messages and subfolders first").assertExists()
        compose.onNode(hasText("Delete folder") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun anEmptyFolderIsDeletedAfterAskingAndThePageLeadsBack() {
        server.folders += """{"id": "t", "name": "Travel"}"""
        openFolder("Travel")
        compose.onNodeWithText("No messages").assertExists()
        compose.onNode(hasText("Delete folder") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Delete Travel?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(TIMEOUT) { server.calls.any { "Mailbox/set" in it && "\"destroy\":[\"t\"]" in it } }
        compose.waitForText("Drag to reorder.")
        compose.waitUntil(TIMEOUT) { count("Travel") == 0 }
    }

    @Test
    fun choosingAColorKeepsItOnThisDevice() {
        openFolder("Receipts")
        compose.onNode(hasText("Color") and hasClickAction()).performClick()
        compose.onNode(hasText("Green") and isSelectable()).performClick()
        assertEquals(mapOf("r" to "Green"), store.readColors().names.mapKeys { it.key.mailboxId })
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(TIMEOUT) { count("Done") == 0 }
        compose.onNode(hasText("Color") and hasText("Green") and hasClickAction()).assertExists()
        assertTrue("Colors stay off the server", server.calls.none { "Mailbox/set" in it })
    }

    @Test
    fun rulesHideWhenTheServerHasNoSieve() {
        server.sieve = false
        openFolder("Receipts")
        compose.onNode(hasText("Color") and hasClickAction()).assertExists()
        compose.onNodeWithText("Rules").assertDoesNotExist()
        compose.onNodeWithText("Add rule").assertDoesNotExist()
    }

    @Test
    fun aSharedMailboxHasNoColorOrRules() {
        server.sharedFolders += """{"id": "d", "name": "Design reviews", "myRights": {"mayAddItems": false}}"""
        openFolder("Design reviews")
        compose.waitForCall(server, "SieveScript/get")
        compose.waitForIdle()
        compose.onNode(hasText("Color") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("Rules").assertDoesNotExist()
    }

    @Test
    fun moveIntoPutsTheFolderUnderTheChosenParent() {
        server.folders += """{"id": "t", "name": "Travel"}"""
        openFolder("Travel")
        compose.onNodeWithText("Top level").performClick()
        compose.onNodeWithText("Receipts / Amazon").assertExists()
        compose.onNode(hasText("Receipts") and isSelectable()).performClick()
        compose.waitUntil(TIMEOUT) { server.calls.any { "Mailbox/set" in it && "\"parentId\":\"r\"" in it } }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
