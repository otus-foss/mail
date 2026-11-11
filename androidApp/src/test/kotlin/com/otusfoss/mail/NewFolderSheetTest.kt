package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.otusfoss.mail.app.App
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The New folder sheet's rules and color, given to the folder once the server creates it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NewFolderSheetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val colors = inMemoryFolderColorStore()

    private fun openNewFolder() {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(folderColorStore = colors), behaviorState = MailBehaviorState())
            }
        }
        // Suggestions come from loaded mail, and Add rule from the loaded script.
        compose.waitForText("Welcome")
        compose.waitForCall(server, "SieveScript/get")
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Folders & rules").performClick()
        compose.waitForText("Receipts")
        compose.onNode(hasContentDescription("New folder") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Name").performTextInput("Travel")
    }

    @Test
    fun aNewFolderStartsWithTheRulesAndColorChosenBeforeCreate() {
        openNewFolder()
        compose.onNodeWithText("Add rule").performClick()
        compose.onNodeWithText("Sort into Travel").assertExists()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("grace")
        compose.onNode(hasText("grace@example.org") and isSelectable()).performClick()
        compose.onNodeWithText("Add").performClick()
        compose.waitForText("grace@example.org")

        // No folder has a color yet, so the first one is preselected.
        compose.onNodeWithText("Red").performClick()
        compose.onNodeWithText("Blue").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Done")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Blue").assertExists()

        compose.onNodeWithText("Create").performClick()
        compose.waitForCall(server, "SieveScript/set")
        assertTrue(server.script.orEmpty(), "grace@example.org" in server.script.orEmpty())
        assertEquals(listOf("Blue"), colors.read().values.toList())
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
