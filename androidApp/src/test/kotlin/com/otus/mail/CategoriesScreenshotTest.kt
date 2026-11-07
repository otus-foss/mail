package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.OtusColors
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.mail.core.categories.domain.CategoryReason
import com.otus.mail.core.categories.domain.CategoryVerdict
import com.otus.mail.core.categories.domain.InboxCategories
import com.otus.mail.core.categories.domain.MailCategory
import com.otus.mail.core.categories.domain.ReasonKind
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.core.settings.domain.ListBehavior
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.feature.mailsettings.presentation.components.CategoriesSheet
import com.otus.mail.feature.read.presentation.ReadCategories
import com.otus.mail.feature.read.presentation.components.CategorySheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Inbox's category pills, the Categories settings sheet, and the Read view's category sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoriesScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val categories = InboxCategories(enabled = true)

    @Test
    fun pillsLeadTheInboxRow() =
        listOf(ThemeBase.Otus, ThemeBase.Light, ThemeBase.Black).forEach { base ->
            compose.showControlInbox(palette(base), categories = categories)
            compose.screenshot("inbox/categories_${base.name.lowercase()}")
        }

    @Test
    fun aPillShowsItsShareOfTheMail() {
        compose.showControlInbox(categories = categories)
        compose.onNodeWithText("Transactions").performClick()
        compose.screenshot("inbox/categories_transactions")
    }

    @Test
    fun settingsSheet() {
        val hidden = categories.withShown(MailCategory.Notifications, shown = false)
        val state = MailBehaviorState(initial = MailBehavior(list = ListBehavior(categories = hidden)))
        show { CategoriesSheet(state) {} }
        compose.screenshot("settings/categories_sheet")
    }

    @Test
    fun whySheet() {
        val newsletter =
            MailMessage(
                "ada/e1",
                MailSender("Weekly Digest", "digest@weekly.example"),
                "This week in design",
                "",
                ReceivedAt("2026-11-01", "09:00"),
                MailLocation("Inbox", "Ada", "ada"),
                category =
                    CategoryVerdict(
                        MailCategory.Newsletters,
                        listOf(CategoryReason(ReasonKind.ListHeaders, "List-Unsubscribe, Precedence: bulk")),
                    ),
            )
        show { CategorySheet(newsletter, ReadCategories({ categories }) { _, _ -> }) {} }
        compose.screenshot("read/category_sheet")
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme { Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) { content() } }
            }
        }
        compose.waitForIdle()
    }
}
