package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.mail.app.App
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spam and Trash pages show their own bars, against a server that answers like Stalwart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    /** Opens the [folder] page from the sidebar and then its one message, [subject]. */
    private fun openMessageIn(
        folder: String,
        subject: String,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(
                    services = server.services(),
                    behaviorState =
                        MailBehaviorState(
                            initial = MailBehavior(read = ReadBehavior(markReadOnOpen = false)),
                        ),
                )
            }
        }
        compose.waitForText("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val entry = hasContentDescription(folder, substring = true)
        compose.waitForNode(entry)
        compose.onNode(entry).performClick()
        compose.waitForText(subject)
        compose.onNodeWithText(subject).performClick()
        // Folder actions appear once folders and rules have loaded.
        compose.waitForCall(server, "SieveScript/get")
        compose.waitForIdle()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    private fun lastUpdate(): String {
        compose.waitForCall(server, "Email/set")
        return server.calls.last { "Email/set" in it }
    }

    @Test
    fun notSpamMovesTheMessageToTheInboxAndMarksIt() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk", "totalEmails": 1}"""
        server.subjects["j"] = "You won a prize"
        openMessageIn("Spam", "You won a prize")
        compose.screenshot("folders/spam_bar")
        button("Delete forever").assertExists()
        button("Not spam").performClick()
        val update = lastUpdate()
        val patch =
            "\"m1-j\":{\"mailboxIds/j\":null,\"mailboxIds/a\":true," +
                "\"keywords/\$junk\":null,\"keywords/\$notjunk\":true}"
        assertTrue(update, patch in update)
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun deleteForeverAsksFirstAndThenDestroysTheMessage() {
        server.folders += """{"id": "t", "name": "Deleted Items", "role": "trash", "totalEmails": 1}"""
        server.subjects["t"] = "Old receipt"
        openMessageIn("Trash", "Old receipt")
        compose.screenshot("folders/trash_bar")
        button("Restore").assertExists()

        button("Delete forever").performClick()
        compose.onNodeWithText("Delete this message forever?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertTrue("Cancel deletes nothing", server.calls.none { "Email/set" in it })

        button("Delete forever").performClick()
        compose.screenshot("folders/delete_forever_confirm")
        compose.onNodeWithText("Delete").performClick()
        val destroy = lastUpdate()
        assertTrue(destroy, "\"destroy\":[\"m1-t\"]" in destroy)
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }
}
