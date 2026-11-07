package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.presentation.FolderPillOption
import com.otus.mail.core.settings.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The search's Accounts sheet lists saved accounts that have no loaded mail. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyAccountPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val loaded = mutableListOf<MailScope?>()
    private val accounts = listOf(MailLocation("", "Empty", "empty", "empty@example.org"))
    private val mailboxes = listOf(MailboxScope.Inbox, MailboxScope(null, "Receipts"))

    private fun show(pills: InboxPills = InboxPills()) {
        val context =
            inboxContext {
                this.pills = pills
                mailboxSearch = MailboxSearch(mailboxes, accounts) { loaded += it }
            }
        compose.runOnUiThread {
            compose.activity.setContent { OtusTheme { InboxScreen(emptyList(), context = context) } }
        }
    }

    @Test
    fun savedAccountsWithNoMailCanBeChosenAndStayNamed() {
        show()
        compose.openSearch()
        compose.onNodeWithContentDescription("All accounts, Inbox").performScrollTo().performClick()
        compose.pickRow("Empty").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Empty account, Inbox").performScrollTo().performClick()
        compose.pickRow("Empty").assertIsOn()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals(setOf("empty"), (loaded.last() as MailScope.Mailboxes).accounts)
        }
    }

    @Test
    fun aFolderPillKeepsTheAccountsSheetInsideItsFolders() {
        val pill = InboxPill.ReadLater
        val scope = MailScope.Folders(setOf(MailScope.Folder("empty", "later"), MailScope.Folder("other", "later")))
        val option = FolderPillOption(pill, "Read later", Res.drawable.symbol_folder, scope, "Folders")
        show(InboxPills(listOf(pill), listOf(option)))
        compose.onNode(hasText("Read later") and isToggleable()).performClick()
        compose.openSearch("Search Read later")
        compose.onNodeWithText("All accounts").performScrollTo().performClick()
        compose.onNodeWithText("Inboxes").assertDoesNotExist()
        compose.pickRow("Empty").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Empty account").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(MailScope.Folders(setOf(MailScope.Folder("empty", "later"))), loaded.last())
        }
    }
}
