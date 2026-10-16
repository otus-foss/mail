package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarSection
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
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
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Receipts") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
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
        compose
            .onNodeWithText("Choose an account to see all its addresses, or one address. Long-press to pin.")
            .assertIsDisplayed()
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
        compose.onNodeWithText("Choose an address to see only its mail. Long-press to pin.").assertIsDisplayed()
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
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Welcome")).fetchSemanticsNodes().isNotEmpty() }
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
        compose.onNodeWithText("Appearance & behavior").performClick()
        compose.onNode(hasText("Sidebar") and hasText("Edit")).performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Drag a section to reorder it. One section stays on. Changes apply to every account.",
            ).assertIsDisplayed()
        compose.onNode(hasText("Shared by all accounts") and isToggleable()).performClick()
        assertEquals(setOf(SidebarSection.Folders), behavior.behavior.sidebar.hidden)
        compose.onNodeWithText("Grid").performClick()
        compose.onNodeWithText("Rows").performClick()
        closeChoice()
        compose.onNode(hasText("Next to mailboxes and folders")).performClick()
        compose.onNodeWithText("Off").performClick()
        closeChoice()
        assertEquals(MailboxStyle.Rows, behavior.behavior.sidebar.mailboxes)
        assertEquals(SidebarCount.None, behavior.behavior.sidebar.counts)
        // Back leads to Appearance & behavior, then Settings.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Receipts") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasText("Inbox") and isSelectable()).assertIsDisplayed()
        compose.onNode(hasText("Inbox") and hasText("1")).assertDoesNotExist()
    }

    private fun closeChoice() {
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Done")).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
