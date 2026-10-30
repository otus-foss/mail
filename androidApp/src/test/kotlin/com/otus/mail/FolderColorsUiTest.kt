package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.otus.mail.app.App
import com.otus.mail.feature.folders.domain.readColors
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Choosing folder colors from the sidebar, and the switch that hides them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderColorsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()
    private val store = InMemoryFolderColorStore()

    /** Starts the app and opens the sidebar, where a long press offers a folder's color. */
    private fun openSidebar() {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(folderColorStore = store), behaviorState = behavior)
            }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForNode(folderRow("Receipts"))
    }

    private fun folderRow(name: String) = hasText(name) and hasClickAction()

    private fun openColorSheet(folder: String) {
        compose.onAllNodes(folderRow(folder))[0].performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("Color").performClick()
    }

    private fun closeSheet() {
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Done")).fetchSemanticsNodes().isEmpty() }
    }

    private fun storedColors() = store.readColors().names.mapKeys { it.key.mailboxId }

    @Test
    fun theColorSheetListsDefaultFirstAndReadsEachColorByName() {
        openSidebar()
        openColorSheet("Receipts")
        val radioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val options = compose.onAllNodes(radioButton).fetchSemanticsNodes()
        val labels =
            options.map {
                val text = it.config.getOrNull(SemanticsProperties.Text)
                text.orEmpty().joinToString()
            }
        assertEquals(listOf("Default", "Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple", "Pink"), labels)
        // The swatch is decoration, so no option carries a description.
        val descriptions = options.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription) }
        assertEquals(emptyList<String>(), descriptions.flatten())
        compose.onNode(hasText("Default") and isSelectable()).assertIsSelected()
    }

    @Test
    fun choosingAColorSavesItForTheFolderAndDefaultForgetsIt() {
        openSidebar()
        openColorSheet("Receipts")
        compose.onNode(hasText("Teal") and isSelectable()).performClick()
        compose.onNode(hasText("Teal") and isSelectable()).assertIsSelected()
        assertEquals(mapOf("r" to "Teal"), storedColors())
        closeSheet()
        openColorSheet("Receipts")
        compose.onNode(hasText("Teal") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Default") and isSelectable()).performClick()
        assertEquals(emptyMap<String, String>(), storedColors())
    }

    @Test
    fun systemFoldersOfferNoColor() {
        openSidebar()
        compose
            .onNode(
                hasContentDescription("Inbox", substring = true) and isSelectable(),
            ).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Color").assertDoesNotExist()
    }

    @Test
    fun turningFolderColorsOffKeepsTheSavedColors() {
        openSidebar()
        openColorSheet("Receipts")
        compose.onNode(hasText("Purple") and isSelectable()).performClick()
        closeSheet()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNode(hasText("Sidebar") and hasText("2 sections")).performScrollTo().performClick()
        compose
            .onNode(hasText("Only changes the folder icon") and isToggleable())
            .performScrollTo()
            .performClick()
        assertFalse(behavior.behavior.sidebar.folderColors)
        assertEquals(mapOf("r" to "Purple"), storedColors())
        // The sheet still shows the saved color, ready for when the switch is back on.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        openColorSheet("Receipts")
        compose.onNode(hasText("Purple") and isSelectable()).assertIsSelected()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
