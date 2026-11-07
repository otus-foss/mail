package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.otus.mail.core.settings.domain.MailboxStyle
import com.otus.mail.core.settings.domain.SidebarCount
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.domain.SidebarSection
import com.otus.mail.core.settings.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Show mail from" and the Sidebar settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarEditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()

    private fun openSidebar(withBob: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(withBob = withBob), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitForNode(hasText("Receipts") and isSelectable())
    }

    private fun openScopeSheet() {
        compose.onNode(hasContentDescription("Show mail from") and hasClickAction()).performClick()
        compose.onNodeWithText("Show mail from").assertIsDisplayed()
    }

    private fun row(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test
    fun choosingAnAccountShowsItsNameAndCountsAndKeepsTheSidebarOpen() {
        openSidebar(withBob = true)
        compose.onNodeWithText("All mail").assertIsDisplayed()
        compose.onNode(hasText("Receipts") and hasText("4") and isSelectable()).assertIsDisplayed()
        openScopeSheet()
        compose.onNodeWithText("2 accounts · 3 addresses").assertIsDisplayed()
        compose.onNodeWithText("Long-press to pin.").assertIsDisplayed()
        compose.onNode(hasText("Bob") and hasText("2 addresses") and hasClickAction()).performClick()
        closeChoice()
        compose.onNodeWithText("Bob").assertIsDisplayed()
        compose.onNodeWithText("2 addresses").assertIsDisplayed()
        compose.onNode(hasText("Receipts") and hasText("2") and isSelectable()).assertIsDisplayed()
    }

    @Test
    fun choosingAnAddressShowsOnlyMailSentToItUntilAllMailIsChosen() {
        openSidebar()
        openScopeSheet()
        compose.onNodeWithText("Long-press to pin.").assertIsDisplayed()
        row("example.org").performClick()
        row("ada@example.org").performClick()
        closeChoice()
        compose.onNodeWithText("ada@example.org").assertIsDisplayed()
        compose.onNodeWithText("Ada").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        // The one message has no To or Cc and another sender, so it does not involve the address.
        compose.onNodeWithText("Welcome").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        openScopeSheet()
        row("All mail").performClick()
        closeChoice()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForNode(hasText("Welcome"))
    }

    @Test
    fun longPressingAnAddressPinsAndUnpinsIt() {
        openSidebar()
        openScopeSheet()
        row("example.org").performClick()
        row("ada@example.org").performTouchInput { longClick() }
        compose.onNodeWithText("Pinned").assertIsDisplayed()
        compose.onAllNodes(hasText("ada@example.org") and hasClickAction()).assertCountEquals(2)
        compose.onAllNodes(hasText("ada@example.org") and hasClickAction())[0].performTouchInput { longClick() }
        compose.onNodeWithText("Pinned").assertDoesNotExist()
    }

    @Test
    fun theSidebarPageHidesFoldersListsMailboxesAsRowsAndTurnsCountsOff() {
        openSidebar()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNode(hasText("Sidebar") and hasText("2 sections")).performScrollTo().performClick()
        compose.onNode(hasText("Folders") and isToggleable()).performClick()
        assertEquals(setOf(SidebarSection.Folders), behavior.behavior.sidebar.layout.hidden)
        compose.onNode(hasText("Counts") and hasClickAction()).performClick()
        compose.onNodeWithText("Off").performClick()
        closeChoice()
        // Show as lives on the Pinned page, beside the editor it changes.
        compose.onNode(hasText("Mailboxes and folders")).performClick()
        compose.onNodeWithText("Grid").performClick()
        compose.onNodeWithText("Rows").performClick()
        closeChoice()
        assertEquals(MailboxStyle.Rows, behavior.behavior.sidebar.layout.mailboxes)
        assertEquals(SidebarCount.None, behavior.behavior.sidebar.layout.counts)
        // Back leads to Sidebar, then Mail behavior, then Settings.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Receipts") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasText("Inbox") and isSelectable()).assertIsDisplayed()
        compose.onNode(hasText("Inbox") and hasText("1")).assertDoesNotExist()
    }

    @Test
    fun aPinnedFolderShowsInTheSidebarAndStaysInFolders() {
        openSidebar()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNode(hasText("Sidebar") and hasText("2 sections")).performScrollTo().performClick()
        compose.onNode(hasText("Mailboxes and folders")).performClick()
        // The Inbox and Contacts need no folder of their own, so they are the pins shown at first.
        compose.onNode(hasContentDescription("Remove Contacts")).assertIsDisplayed()
        row("Receipts").performScrollTo().performClick()
        assertEquals(
            SidebarPin.defaults + SidebarPin.Folder(listOf("receipts")),
            behavior.behavior.sidebar.layout.pinned,
        )
        compose.onNode(hasContentDescription("Remove Inbox")).performClick()
        assertEquals(false, SidebarPin.Inbox in behavior.behavior.sidebar.layout.pinned)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onAllNodes(hasText("Receipts") and isSelectable()).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Receipts, 2") and isSelectable()).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Inbox", substring = true) and isSelectable()).assertCountEquals(0)
    }

    @Test
    fun rowsSwapTheTileEditorForOrderedRowsAndHiddenPinsWaitForTheirSection() {
        behavior.updateSidebar { it.copy(layout = it.layout.copy(mailboxes = MailboxStyle.Rows)) }
        openSidebar()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNode(hasText("Sidebar") and hasText("2 sections")).performScrollTo().performClick()
        compose.onNode(hasText("Mailboxes and folders")).performClick()
        // Rows have no sizes, so each pin is a row with a reorder handle rather than a tile to resize.
        compose.onNodeWithContentDescription("Reorder Contacts").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Contacts").performClick()
        assertFalse(SidebarPin.Contacts in behavior.behavior.sidebar.layout.pinned)
        compose.onNodeWithContentDescription("Back").performClick()
        // With the Pinned section off nothing shows the pins, so their page waits and says why.
        compose.onNode(hasText("Pinned") and isToggleable()).performClick()
        compose.onNode(hasText("Mailboxes and folders")).assertIsNotEnabled()
        compose.onNodeWithText("Turn on the Pinned section to arrange it").assertIsDisplayed()
    }

    private fun closeChoice() {
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Done")).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
