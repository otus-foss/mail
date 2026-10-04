package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.otus.mail.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchIsEmptyInboxAndSearchCanBeClearedAndClosed() {
        compose.onNodeWithText("Your inbox is empty").assertIsDisplayed()
        compose.onNodeWithText("Context panel playground").assertDoesNotExist()
        screenshot("empty-inbox")
        compose.onNodeWithText("Tap to search").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").assertIsFocused().performTextInput("hello")
        compose.onNodeWithText("Nothing to search yet").assertIsDisplayed()
        screenshot("empty-search")
        compose.onNodeWithContentDescription("Clear search").performClick().assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithText("Your inbox is empty").assertIsDisplayed()
    }

    @Test
    fun searchTransitionCanReverseBeforeItFinishes() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithContentDescription("Search your mailbox…").assertExists()
        compose.onNodeWithContentDescription("Search mailbox").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("Search mailbox").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithContentDescription("Search your mailbox…").assertIsFocused()
        compose.onNodeWithContentDescription("Search mailbox").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithContentDescription("Search your mailbox…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search mailbox").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun liveSearchFiltersFixturesAndResetRestoresList() {
        showFixtures()
        compose.onNodeWithText("Design review").assertIsDisplayed()
        screenshot("inbox-fixtures")
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("prototype")
        compose.onNodeWithText("Design review").assertIsDisplayed()
        compose.onNodeWithText("Weekend plans").assertDoesNotExist()
        screenshot("search-fixtures")
        compose.onNodeWithContentDescription("Search your mailbox…").performTextReplacement("nothing matches")
        compose.onNodeWithText("No matching messages").assertIsDisplayed()
        compose.onNodeWithText("Clear search and filters").performClick()
        compose.onNodeWithText("Weekend plans").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Search mailbox").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun filterChoicesActuallyRestrictMessagesAtNarrowWidth() {
        showFixtures()
        compose.onNodeWithText("Unread").performClick().assertIsOn()
        compose.onNodeWithText("Weekend plans").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("People").performClick()
        compose.onNode(hasText("alex@example.com") and isToggleable()).performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Design review").assertIsDisplayed()
        compose.onNodeWithText("Accounts").performScrollTo().performClick()
        compose.onNodeWithText("Personal").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("No matching messages").assertIsDisplayed()
        screenshot("narrow-filtered")
    }

    @Test
    fun searchInputSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("saved query")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("saved query").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithText("Your inbox is empty").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun emptySearchRemainsUsableWithLargeTextAndLimitedHeight() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { InboxScreen(emptyList()) }
                }
            }
        }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").performTextInput("hello")
        compose.onNodeWithText("Clear search and filters").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        screenshot("large-text-empty")
    }

    private fun showFixtures() {
        val messages =
            listOf(
                MailMessage(
                    "alex",
                    MailSender("Alex", "alex@example.com"),
                    "Design review",
                    "The prototype is ready",
                    ReceivedAt("2026-10-03", "10:30"),
                    MailLocation("Inbox", "Work"),
                    MailAttributes(true, listOf("draft.pdf")),
                ),
                MailMessage(
                    "sam",
                    MailSender("Sam", "sam@example.com"),
                    "Weekend plans",
                    "A walk by the lake",
                    ReceivedAt("2026-10-02", "09:00"),
                    MailLocation("Inbox", "Personal"),
                ),
            )
        compose.runOnUiThread { compose.activity.setContent { OtusTheme { InboxScreen(messages) } } }
    }

    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val file = File("build/reports/inbox/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
