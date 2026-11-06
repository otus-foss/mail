package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.InboxSearch
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.domain.searchMessages
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.components.InboxFilter
import com.otus.mail.feature.inbox.presentation.components.InboxFilterSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountIdentityPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sameNamedAccountsHaveSeparateSelectableRows() {
        val messages = listOf(message("work", "ada@work.example"), message("home", "ada@home.example"))
        var applied = InboxSearch()
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    InboxFilterSheet(
                        InboxFilter.Accounts,
                        messages,
                        InboxSearch(),
                        InboxContext(),
                        DateFormatter(),
                        { applied = it },
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("ada@work.example").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(setOf("work"), applied.accounts)
        assertEquals(listOf("work"), searchMessages(messages, applied).map { it.id })
    }

    private fun message(
        id: String,
        address: String,
    ) = MailMessage(
        id,
        MailSender("Sender", "sender@example.org"),
        "Subject",
        "Body",
        ReceivedAt("2026-10-01", "12:00"),
        MailLocation("Inbox", "Ada", id, address, mailboxRole = MailboxRole.Inbox),
    )
}
