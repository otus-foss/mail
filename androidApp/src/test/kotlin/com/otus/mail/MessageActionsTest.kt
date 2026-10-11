package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Marks, archive, and trash on an open message, against a server that answers like Stalwart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openMessage() {
        compose.runOnUiThread { compose.activity.setContent { App(services = server.services()) } }
        waitFor("Welcome")
        compose.onNodeWithText("Welcome").performClick()
        // Folder actions appear once folders and rules have loaded.
        waitFor("Rule for sender")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun lastUpdate(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it } }
        return server.calls.last { "Email/set" in it }
    }

    @Test
    fun marksChangeOnTheServerAndInTheOpenMessage() {
        openMessage()
        compose.onNodeWithText("Mark read").performClick()
        compose.onNodeWithText("Mark unread").assertIsDisplayed()
        val read = lastUpdate()
        assertTrue(read, "\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in read)

        compose.onNodeWithText("Flag").performClick()
        compose.onNodeWithText("Unflag").assertIsDisplayed()
        compose.waitUntil(TIMEOUT) { server.calls.count { "Email/set" in it } == 2 }
        val flag = lastUpdate()
        assertTrue(flag, "\"update\":{\"m1\":{\"keywords/\$flagged\":true}}" in flag)
    }

    @Test
    fun archiveAndTrashFileTheMessageIntoTheFoldersWithThoseRoles() {
        server.folders += """{"id": "x", "name": "Old mail", "role": "archive"}"""
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        openMessage()
        compose.onNodeWithText("Archive").performClick()
        val archive = lastUpdate()
        assertTrue(archive, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/x\":true}}" in archive)
        compose.onNodeWithText("Close").assertDoesNotExist()

        compose.onNodeWithText("Welcome").performClick()
        waitFor("Trash")
        compose.onNodeWithText("Trash").performClick()
        compose.waitUntil(TIMEOUT) { server.calls.count { "Email/set" in it } == 2 }
        val trash = lastUpdate()
        assertTrue(trash, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/t\":true}}" in trash)
    }

    @Test
    fun actionsWithoutAFolderRoleOrPermissionAreNotOffered() {
        server.folders[0] =
            """{"id": "a", "name": "Inbox", "role": "inbox", "myRights": {"maySetKeywords": false}}"""
        openMessage()
        compose.onNodeWithText("Mark read").assertIsDisplayed()
        compose.onNodeWithText("Flag").assertDoesNotExist()
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithText("Trash").assertDoesNotExist()
    }

    @Test
    fun aRefusedMarkIsUndoneAndReported() {
        server.refuseUpdates = true
        openMessage()
        compose.onNodeWithText("Flag").performClick()
        waitFor("Couldn't change the message")
        compose.onNodeWithText("Flag").assertIsDisplayed()
        compose.onNodeWithText("Unflag").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
