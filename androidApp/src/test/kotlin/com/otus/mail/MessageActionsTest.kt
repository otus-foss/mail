package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The open message's bar and menu, against a server that answers like Stalwart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openMessage(behavior: MailBehavior = MailBehavior()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(), behaviorState = MailBehaviorState(initial = behavior))
            }
        }
        waitFor("Welcome")
        compose.onNodeWithText("Welcome").performClick()
        // Folder actions appear once folders and rules have loaded.
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
        compose.waitForIdle()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    /** Opens the page's menu, checks it offers [label], and closes it again. */
    private fun menuOffers(label: String): Boolean {
        compose.onNodeWithContentDescription("More actions").performClick()
        val offered = compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        // The scrim's center is under the menu, so tap its corner.
        compose.onNodeWithContentDescription("Dismiss More actions").performTouchInput { click(Offset(1f, 1f)) }
        return offered
    }

    private fun chooseFromMenu(label: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText(label).performClick()
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
    fun openingAMessageMarksItReadAndMarksChangeOnTheServer() {
        openMessage()
        val read = lastUpdate()
        assertTrue(read, "\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in read)

        chooseFromMenu("Mark unread")
        assertTrue(menuOffers("Mark read"))
        compose.waitUntil(TIMEOUT) { server.calls.count { "Email/set" in it } == 2 }
        val unread = lastUpdate()
        assertTrue(unread, "\"update\":{\"m1\":{\"keywords/\$seen\":null}}" in unread)

        chooseFromMenu("Flag")
        assertTrue(menuOffers("Unflag"))
        compose.waitUntil(TIMEOUT) { server.calls.count { "Email/set" in it } == 3 }
        val flag = lastUpdate()
        assertTrue(flag, "\"update\":{\"m1\":{\"keywords/\$flagged\":true}}" in flag)
    }

    @Test
    fun aMessageStaysUnreadWhenOpeningIsSetNotToMarkIt() {
        openMessage(MailBehavior(markReadOnOpen = false))
        assertTrue(menuOffers("Mark read"))
        assertEquals(0, server.calls.count { "Email/set" in it })
    }

    @Test
    fun archiveFilesTheMessageIntoTheArchiveFolderAndTheRowStaysOut() {
        server.folders += """{"id": "x", "name": "Old mail", "role": "archive"}"""
        openMessage(MailBehavior(markReadOnOpen = false))
        button("Archive").performClick()
        val archive = lastUpdate()
        assertTrue(archive, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/x\":true}}" in archive)
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()

        // A successful move refreshes the folders but not the list, which this server would refill.
        val afterArchive = server.calls.indexOfFirst { "Email/set" in it }
        compose.waitUntil(TIMEOUT) { server.calls.drop(afterArchive).any { "Mailbox/get" in it } }
        compose.waitForIdle()
        assertTrue(server.calls.drop(afterArchive).none { "Email/query" in it })
        compose.onNodeWithText("Welcome").assertDoesNotExist()
    }

    @Test
    fun trashFilesTheMessageIntoTheTrashFolder() {
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        openMessage(MailBehavior(markReadOnOpen = false))
        button("Delete").performClick()
        val trash = lastUpdate()
        assertTrue(trash, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/t\":true}}" in trash)
    }

    @Test
    fun readLaterSavesTheMessageInPlaceAndCanRemoveIt() {
        server.folders += """{"id": "l", "name": "Read later"}"""
        openMessage(MailBehavior(markReadOnOpen = false, readLater = true))
        chooseFromMenu("Read later")
        val save = lastUpdate()
        assertTrue(save, "\"update\":{\"m1\":{\"mailboxIds/l\":true}}" in save)

        // The message stays open and in the inbox, now offering to leave Read later.
        chooseFromMenu("Remove from Read later")
        compose.waitUntil(TIMEOUT) { server.calls.count { "Email/set" in it } == 2 }
        val remove = lastUpdate()
        assertTrue(remove, "\"update\":{\"m1\":{\"mailboxIds/l\":null}}" in remove)
    }

    @Test
    fun trashTakesTheMessageOutOfEveryFolder() {
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        server.folders += """{"id": "l", "name": "Read later"}"""
        server.alsoIn += "l"
        openMessage(MailBehavior(markReadOnOpen = false, readLater = true))
        button("Delete").performClick()
        val trash = lastUpdate()
        val patch = "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/l\":null,\"mailboxIds/t\":true}}"
        assertTrue(trash, patch in trash)
    }

    @Test
    fun actionsWithoutAFolderRoleOrPermissionAreNotOffered() {
        server.folders[0] =
            """{"id": "a", "name": "Inbox", "role": "inbox", "myRights": {"maySetKeywords": false}}"""
        // Read later is off, so its folder is an ordinary one.
        server.folders += """{"id": "l", "name": "Read later"}"""
        openMessage(MailBehavior(markReadOnOpen = false))
        // Unavailable actions keep their slots, disabled, so the bar never shifts.
        listOf("Archive", "Delete").forEach { button(it).assertIsNotEnabled() }
        assertFalse(menuOffers("Flag"))
        assertTrue(menuOffers("Mark read"))
        assertFalse(menuOffers("Read later"))
    }

    @Test
    fun aRefusedMarkIsUndoneAndReported() {
        server.refuseUpdates = true
        openMessage(MailBehavior(markReadOnOpen = false))
        chooseFromMenu("Flag")
        waitFor("Couldn't change the message")
        assertTrue(menuOffers("Flag"))
        assertFalse(menuOffers("Unflag"))
    }

    @Test
    fun moveFilesTheMessageIntoTheChosenFolderAndClosesIt() {
        server.folders += """{"id": "p", "name": "Projects"}"""
        openMessage(MailBehavior(markReadOnOpen = false))
        button("Move").performClick()
        compose.onNodeWithText("Projects").performClick()
        val move = lastUpdate()
        assertTrue(move, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/p\":true}}" in move)
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun backClosesTheMessage() {
        openMessage(MailBehavior(markReadOnOpen = false))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
        compose.onNodeWithText("Welcome").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
