package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.ScheduledSends
import com.otus.mail.core.messages.domain.SentMessage
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.feature.compose.presentation.ComposeAccount
import com.otus.mail.feature.compose.presentation.ComposeExits
import com.otus.mail.feature.compose.presentation.ComposeOrigin
import com.otus.mail.feature.compose.presentation.ComposeServices
import com.otus.mail.feature.compose.presentation.ComposeState
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import com.otus.mail.feature.compose.presentation.screens.ComposeScreen
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Send-from list with enough addresses for a search, scrolled to its end, in the Light and Black themes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FromSearchScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val addresses = (1..10).map { "alias$it@companya.com" }

    @Test
    fun searchStaysAboveScrolledAddressesLight() = scrolledShot("compose_from_search_scrolled_light", ThemeBase.Light)

    @Test
    fun searchStaysAboveScrolledAddressesBlack() = scrolledShot("compose_from_search_scrolled_black", ThemeBase.Black)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun scrolledShot(
        name: String,
        theme: ThemeBase,
    ) {
        open(theme)
        compose.onNode(hasContentDescription("Send from this@companya.com. Change") and hasClickAction()).performClick()
        compose.onNodeWithText("otus@rubylabs.design").performScrollTo()
        compose.onNodeWithText("Search 13 addresses").assertExists()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    private fun open(theme: ThemeBase) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme(ThemeChoice(theme).palette) {
                    val scope = rememberCoroutineScope()
                    val state =
                        remember {
                            ComposeState(
                                ComposeServices(TwoAccountDrafts(addresses), { _, _ -> emptyList() }, NoScheduling),
                                scope,
                                ComposeRequest.New(),
                                ComposeOrigin(
                                    "a1",
                                    listOf(ComposeAccount("a1", "Company A"), ComposeAccount("a2", "Rubylabs")),
                                    listOf("this@companya.com"),
                                    recentFrom = listOf("this@companya.com"),
                                ),
                                saveDelayMillis = 60_000,
                            )
                        }
                    ComposeScreen(state, MailBehavior(recentFrom = listOf("this@companya.com")), NoExits)
                }
            }
        }
        compose.waitForIdle()
    }

    private object NoScheduling : ScheduledSends {
        override suspend fun maxDelaySeconds(accountId: String) = 0L

        override suspend fun cancel(message: SentMessage) = Result.success(Unit)

        override suspend fun sendNow(message: SentMessage) = Result.success(Unit)
    }

    private object NoExits : ComposeExits {
        override fun close() = Unit

        override fun discarded(delete: () -> Unit) = Unit

        override fun send(
            draft: OutgoingDraft,
            at: LocalDateTime?,
        ) = Unit
    }
}
