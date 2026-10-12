package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** The account rail and the Sidebar settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarEditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()
    private val behavior = MailBehaviorState()

    private fun openSidebar() {
        compose.runOnUiThread {
            compose.activity.setContent { App(services = server.services(), behaviorState = behavior) }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Receipts") and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun choosingAnAccountShowsItsAddressAndKeepsTheSidebarOpen() {
        openSidebar()
        compose.onNodeWithText("1 account · 1 unread").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ada").performClick()
        compose.onNodeWithContentDescription("Ada").assertIsSelected()
        compose.onNodeWithContentDescription("All accounts").assertIsNotSelected()
        compose.onNodeWithText("ada@example.org").assertIsDisplayed()
        compose.onNode(hasText("Receipts") and hasText("2") and isSelectable()).assertIsDisplayed()
    }

    @Test
    fun theSidebarPageHidesFoldersListsMailboxesAsRowsAndTurnsCountsOff() {
        openSidebar()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance & behavior").performClick()
        compose.onNode(hasText("Sections, mailboxes, and counts")).performScrollTo().performClick()
        compose
            .onNodeWithText(
                "Drag a section to reorder it. One section stays on. Changes apply to every account.",
            ).assertIsDisplayed()
        compose.onNode(hasText("Shared by all accounts") and isToggleable()).performClick()
        assertEquals(setOf(SidebarSection.Folders), behavior.behavior.sidebar.hidden)
        compose.onNodeWithText("Chips").performClick()
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
