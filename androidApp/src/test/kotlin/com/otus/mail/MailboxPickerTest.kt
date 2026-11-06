package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailScope
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.MailboxScope
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MailboxSearch
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MailboxPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val loaded = mutableListOf<MailScope?>()

    @Before
    fun showInbox() {
        val context =
            InboxContext(
                mailboxSearch =
                    MailboxSearch(
                        listOf(
                            MailboxScope.Inbox,
                            MailboxScope(null, "Archive", MailboxRole.Archive),
                            MailboxScope(null, "Sent", MailboxRole.Sent),
                        ),
                    ) {
                        loaded +=
                            it
                    },
            )
        compose.runOnUiThread { compose.activity.setContent { OtusTheme { InboxScreen(fixtures, context = context) } } }
        compose.openSearchResults()
        compose
            .onNodeWithContentDescription("All accounts, Inbox")
            .performScrollTo()
            .assertIsOff()
            .performClick()
    }

    @Test
    fun choosingAnotherInboxLoadsItAndNamesItInThePill() {
        compose.onNodeWithText("Inboxes").assertIsDisplayed()
        row("Inbox").assertIsOn()
        row("Archive").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose
            .onNodeWithContentDescription("All accounts, Inbox, Archive")
            .performScrollTo()
            .assert(hasText("All accounts") and hasText("Inbox +1"))
            .assertIsOn()
        compose.runOnIdle {
            assertEquals(
                MailScope.Mailboxes(setOf(MailboxScope.Inbox, MailboxScope(null, "Archive", MailboxRole.Archive))),
                loaded.last(),
            )
        }
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.runOnIdle { assertEquals("Closing search returns to the page's messages", null, loaded.last()) }
    }

    @Test
    fun noInboxCheckedSearchesAllOfThem() {
        row("Personal").performClick()
        row("Inbox").performClick().assertIsOff()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(MailScope.Mailboxes(emptySet(), setOf("Personal")), loaded.last()) }
        compose
            .onNodeWithContentDescription("Personal account, All inboxes")
            .performScrollTo()
            .assert(hasText("Personal") and hasText("All inboxes"))
            .performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("All accounts, All inboxes").assertIsOn()
    }

    /** A choice in the sheet; the pill behind it can show the same text. */
    private fun row(name: String) = compose.onNode(hasText(name) and isToggleable() and hasAnyAncestor(isDialog()))

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
