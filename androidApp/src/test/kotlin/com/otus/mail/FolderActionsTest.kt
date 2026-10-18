package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    behaviorState = MailBehaviorState(initial = MailBehavior(markReadOnOpen = false)),
                )
            }
        }
        waitFor("Welcome")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        val entry = hasContentDescription(folder, substring = true)
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(entry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(entry).performClick()
        waitFor(subject)
        compose.onNodeWithText(subject).performClick()
        // Folder actions appear once folders and rules have loaded.
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
        compose.waitForIdle()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

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
    fun notSpamMovesTheMessageToTheInboxAndMarksIt() {
        server.folders += """{"id": "j", "name": "Junk Mail", "role": "junk", "totalEmails": 1}"""
        server.subjects["j"] = "You won a prize"
        openMessageIn("Spam", "You won a prize")
        screenshot("spam-bar")
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
        screenshot("trash-bar")
        button("Restore").assertExists()

        button("Delete forever").performClick()
        compose.onNodeWithText("Delete this message forever?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertTrue("Cancel deletes nothing", server.calls.none { "Email/set" in it })

        button("Delete forever").performClick()
        dialogScreenshot("delete-forever-confirm")
        compose.onNodeWithText("Delete").performClick()
        val destroy = lastUpdate()
        assertTrue(destroy, "\"destroy\":[\"m1-t\"]" in destroy)
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    /** Saves the screen to build/reports/inbox for review. */
    private fun screenshot(name: String) = save(name) { compose.activity.window.decorView }

    /** Saves the dialog on top, which has its own window. */
    private fun dialogScreenshot(name: String) =
        save(name) { requireNotNull(ShadowDialog.getLatestDialog().window).decorView }

    private fun save(
        name: String,
        window: () -> View,
    ) {
        val bitmap =
            compose.runOnIdle {
                val view = window()
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val directory = File("build/reports/inbox").apply { mkdirs() }
        File(
            directory,
            "folder-actions-$name.png",
        ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
