package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun showMessages() {
        compose.runOnUiThread { compose.activity.setContent { OtusTheme { InboxScreen(fixtures, dateFormatter) } } }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
    }

    @Test
    fun pickerSearchPreservesMailQueryAndSelectionsAcrossSearches() {
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("Meeting")
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithContentDescription("Search people…").performTextInput("Alex")
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick().assertIsOn()
        compose.onNodeWithContentDescription("Search people…").performTextReplacement("sam@example.com")
        compose.onNode(hasText("sam@example.com") and isToggleable()).performClick().assertIsOn()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear picker search").performClick()
        screenshot("people")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("From 2 people").assertIsDisplayed().assert(!hasText("Alex"))
        chipScreenshot("From 2 people", "people-chip-multiple")
        compose.onNodeWithText("Meeting").assertIsDisplayed()
        assertResults(2)
    }

    @Test
    fun dismissalDiscardsDraftAndClearOnlyTakesEffectOnDone() {
        compose.onNodeWithText("People").performClick()
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick()
        compose.onNodeWithContentDescription("Dismiss From").performTouchInput { click(Offset(center.x, 10f)) }
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        assertResults(1)
        compose.onNodeWithContentDescription("From Alex").assert(hasText("Alex"))
        chipScreenshot("From Alex", "people-chip-single")
        compose.onNodeWithContentDescription("From Alex").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithContentDescription("Dismiss From").performTouchInput { click(Offset(center.x, 10f)) }
        assertResults(1)
        compose.onNodeWithContentDescription("From Alex").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Done").performClick()
        assertResults(2)
    }

    @Test
    fun swipingTheSheetDownDiscardsTheDraft() {
        compose.onNodeWithText("People").performClick()
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick()
        compose.onNodeWithText("From").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 200)
        }
        compose.onNodeWithContentDescription("Dismiss From").assertDoesNotExist()
        assertResults(2)
    }

    @Test
    fun swipingDownFromContentThatDoesNotScrollClosesTheSheet() {
        listOf(hasContentDescription("Search accounts…"), hasText("1 selected")).forEach { start ->
            compose.onNodeWithText("All accounts").performScrollTo().performClick()
            compose.onNode(start).performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 200)
            }
            compose.onNodeWithContentDescription("Dismiss Accounts").assertDoesNotExist()
        }
    }

    @Test
    fun doubleTappingClearAppliesClearedSelectionAndCloses() {
        compose.onNodeWithText("People").performClick()
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        assertResults(1)
        compose.onNodeWithContentDescription("From Alex").performClick()
        compose.onNodeWithText("Clear").performTouchInput { doubleClick() }
        compose.onNodeWithText("Clear").assertDoesNotExist()
        assertResults(2)
    }

    @Test
    fun draftAndPickerQuerySurviveActivityRecreation() {
        // Re-enter the production composition so saveable keys match across recreation.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("kept query")
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithContentDescription("Search people…").performTextInput("Alex")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("kept query").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open filters").performClick()
        compose.onNodeWithText("Unread only").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Unread only").assertIsOn()
        compose.onNodeWithText("Done").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Open filters").performClick()
        compose.onNodeWithText("Unread only").assertIsOn()
    }

    @Test
    fun accountPickerSearchesAccountsAndRestrictsMessages() {
        compose
            .onNodeWithText("All accounts")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search accounts…").performTextInput("Personal")
        compose.onNode(hasText("Personal") and isToggleable()).performClick()
        screenshot("accounts")
        compose.onNodeWithText("Done").performClick()
        assertResults(1)
        compose.onNodeWithText("Meeting plans").assertIsDisplayed()
        val single = compose.onNodeWithContentDescription("Personal account, Inbox").performScrollTo()
        single.assert(hasText("Inbox") and !hasText("Personal")).assertIsOn()
        chipScreenshot("Personal account, Inbox", "accounts-chip-single")
        single.performClick()
        compose.onNodeWithContentDescription("Search accounts…").performTextReplacement("Work")
        compose.onNode(hasText("Work") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        assertResults(2)
        compose
            .onNodeWithContentDescription("2 accounts, Inbox")
            .performScrollTo()
            .assert(hasText("Inbox"))
            .assertIsOn()
        chipScreenshot("2 accounts, Inbox", "accounts-chip-multiple")
    }

    @Test
    fun dateRangeRequiresBothDatesAndAcceptsReverseSelection() {
        compose.onNodeWithText("Date").performClick()
        val first = LocalDate.now().minusDays(1).toString()
        val last = LocalDate.now().toString()
        compose.onNodeWithContentDescription(last).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithContentDescription(first).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        screenshot("date-range")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText(relativeIsoRange(first, last, "–")).performScrollTo().performClick()
        compose.onNodeWithText(relativeIsoRange(first, last)).assertIsDisplayed()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Done").performClick()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun largeTextSheetKeepsSearchAndActionsReachable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { InboxScreen(fixtures, dateFormatter) }
                }
            }
        }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("People").performClick()
        compose.onNodeWithContentDescription("Search people…").performTextInput("unknown")
        compose.onNodeWithText("No matches. Try another search.").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        screenshot("people-large-text")
        compose.onNodeWithContentDescription("Dismiss From").performTouchInput { click(Offset(center.x, 10f)) }
    }

    /** Each listed conversation's row reads as read or unread. */
    private fun assertResults(count: Int) =
        assertEquals(
            count,
            compose.onAllNodes(hasStateDescription("Read") or hasStateDescription("Unread")).fetchSemanticsNodes().size,
        )

    private fun chipScreenshot(
        description: String,
        name: String,
    ) {
        val bounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInWindow
        val bitmap =
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(full))
                Bitmap.createBitmap(
                    full,
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.width.toInt(),
                    bounds.height.toInt(),
                )
            }
        val file = File("build/reports/pickers/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val view = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val file = File("build/reports/pickers/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val dateFormatter = DateFormatter(DateFormatPreference.YearMonthDayDashes)

    private val fixtures =
        listOf(
            MailMessage(
                "alex",
                MailSender("Alex", "alex@example.com"),
                "Meeting design",
                "",
                ReceivedAt("2026-10-02", "10:00"),
                MailLocation("Inbox", "Work", accountAddress = "me@work.example"),
            ),
            MailMessage(
                "sam",
                MailSender("Sam", "sam@example.com"),
                "Meeting plans",
                "",
                ReceivedAt("2026-10-05", "10:00"),
                MailLocation("Inbox", "Personal", accountAddress = "me@home.example"),
            ),
        )
}
