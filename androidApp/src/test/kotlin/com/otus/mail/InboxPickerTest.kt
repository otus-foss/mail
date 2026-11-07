package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The People, Date, and Accounts chips of the search room and the sheets they open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSearch() {
        compose.runOnUiThread {
            compose.activity.setContent { OtusTheme { InboxScreen(fixtures, dateFormatter, context = inboxContext()) } }
        }
        compose.openSearch()
    }

    @Test
    fun peopleSheetSearchesSendersAndKeepsTheQuery() {
        compose.searchField().performTextInput("Meeting")
        compose.onNodeWithText("People").performClick()
        compose.pickerSearch("people").performTextInput("Alex")
        compose.pickRow("alex@example.com").performClick().assertIsOn()
        compose.pickerSearch("people").performTextReplacement("sam@example.com")
        compose.pickRow("sam@example.com").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Clear picker search").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("From 2 people").assertIsDisplayed()
        compose.showSearchResults()
        compose.onNodeWithText("Meeting").assertIsDisplayed()
        compose.assertListed(2)
    }

    @Test
    fun dismissingTheSheetDropsItsPicksAndClearWaitsForDone() {
        compose.onNodeWithText("People").performClick()
        compose.pickRow("alex@example.com").performClick()
        compose.dismissSheet("From")
        compose.onNodeWithText("People").performClick()
        compose.pickRow("alex@example.com").assertIsOff().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("From Alex").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.dismissSheet("From")
        compose.onNodeWithContentDescription("From Alex").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("From Alex").assertDoesNotExist()
        compose.onNodeWithText("People").assertIsDisplayed()
    }

    @Test
    fun accountsSheetSearchesAccountsAndNarrowsTheResults() {
        compose.onNodeWithContentDescription("All accounts, Inbox").performScrollTo().performClick()
        compose.pickerSearch("accounts").performTextInput("Personal")
        compose.pickRow("Personal").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.showSearchResults()
        compose.assertListed(1)
        compose.onNodeWithText("Meeting plans").assertIsDisplayed()
        compose.searchField().performClick()
        compose.onNodeWithContentDescription("Personal account, Inbox").performScrollTo().performClick()
        compose.pickRow("Work").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.showSearchResults()
        compose.assertListed(2)
        compose.onNodeWithContentDescription("2 accounts, Inbox").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dateSheetPicksARangeInEitherOrder() {
        val first = LocalDate.now().minusDays(1).toString()
        val last = LocalDate.now().toString()
        compose.onNodeWithText("Date").performScrollTo().performClick()
        compose.onNodeWithContentDescription(last).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(DAY_TAP_MILLIS)
        compose.onNodeWithContentDescription(first).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(DAY_TAP_MILLIS)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText(relativeIsoRange(first, last, "–")).performScrollTo().assertIsDisplayed()
        compose.showSearchResults()
        compose.assertListed(1)
        compose.onNodeWithText("Meeting design").assertIsDisplayed()
    }

    private val dateFormatter = DateFormatter(DateFormatPreference.YearMonthDayDashes)

    private val fixtures =
        listOf(
            MailMessage(
                "alex",
                MailSender("Alex", "alex@example.com"),
                "Meeting design",
                "",
                ReceivedAt(LocalDate.now().toString(), "10:00"),
                inbox("Work", "me@work.example"),
            ),
            MailMessage(
                "sam",
                MailSender("Sam", "sam@example.com"),
                "Meeting plans",
                "",
                ReceivedAt(LocalDate.now().minusDays(DAYS_BEFORE).toString(), "10:00"),
                inbox("Personal", "me@home.example"),
            ),
        )

    private fun inbox(
        account: String,
        address: String,
    ) = MailLocation("Inbox", account, accountAddress = address, mailboxRole = MailboxRole.Inbox)

    private companion object {
        /** Longer than the double-tap timeout, so two taps on days pick a range. */
        const val DAY_TAP_MILLIS = 400L
        const val DAYS_BEFORE = 10L
    }
}
