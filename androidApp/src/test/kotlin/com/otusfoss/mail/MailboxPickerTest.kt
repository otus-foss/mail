package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.MailboxScope
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.feature.inbox.presentation.MailboxSearch
import com.otusfoss.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Inboxes under the search's accounts load mail beyond the Inbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MailboxPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val loaded = mutableListOf<MailScope?>()
    private val archive = MailboxScope(null, "Archive", MailboxRole.Archive)

    @Before
    fun openAccounts() {
        val search =
            MailboxSearch(listOf(MailboxScope.Inbox, archive, MailboxScope(null, "Sent", MailboxRole.Sent))) {
                loaded += it
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme { InboxScreen(fixtures, context = inboxContext { mailboxSearch = search }) }
            }
        }
        compose.openSearch()
        compose.onNodeWithContentDescription("All accounts, Inbox").performScrollTo().performClick()
    }

    @Test
    fun choosingAnotherInboxLoadsItAndNamesItInTheChip() {
        compose.onNodeWithText("Inboxes").assertIsDisplayed()
        compose.pickRow("Inbox").assertIsOn()
        compose.pickRow("Archive").performClick()
        compose.onNodeWithText("Done").performClick()
        compose
            .onNodeWithContentDescription("All accounts, Inbox, Archive")
            .performScrollTo()
            .assert(hasText("All accounts") and hasText("Inbox +1"))
        compose.runOnIdle { assertEquals(MailScope.Mailboxes(setOf(MailboxScope.Inbox, archive)), loaded.last()) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals("Closing search returns to the page's messages", null, loaded.last()) }
    }

    @Test
    fun noInboxTickedSearchesAllOfThem() {
        compose.pickRow("Personal").performClick()
        compose.pickRow("Inbox").performClick().assertIsOff()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(MailScope.Mailboxes(emptySet(), setOf("Personal")), loaded.last()) }
        compose
            .onNodeWithContentDescription("Personal account, All inboxes")
            .performScrollTo()
            .assert(hasText("Personal") and hasText("All inboxes"))
            .performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("All accounts, All inboxes").performScrollTo().assertIsDisplayed()
    }

    private val fixtures =
        listOf(
            MailMessage(
                "alex",
                MailSender("Alex", "alex@example.com"),
                "Meeting design",
                "",
                ReceivedAt("2026-10-02", "10:00"),
                MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
            ),
            MailMessage(
                "sam",
                MailSender("Sam", "sam@example.com"),
                "Meeting plans",
                "",
                ReceivedAt("2026-10-05", "10:00"),
                MailLocation("Inbox", "Personal", mailboxRole = MailboxRole.Inbox),
            ),
        )
}
