package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.ThemeBase
import com.otus.designsystem.theme.ThemeChoice
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.screens.NotificationsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppleNotificationsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val apple = PushDistributor("apple", "Apple Push", isApple = true)

    @Test
    fun appleDeliveryExplainsPrivacyAndDoesNotOfferAndroidServices() {
        listOf(ThemeBase.Otus, ThemeBase.Light, ThemeBase.Black).forEach { theme ->
            show(theme, PushDeliveryStatus.Active)
            compose.onNodeWithText("Not instant").assertDoesNotExist()
            compose.onNodeWithText("Google Play services").assertDoesNotExist()
            capture("apple-${theme.name.lowercase()}")
        }
    }

    @Test
    fun choosingAndDisablingAppleNeverPromisesPeriodicChecks() {
        show(ThemeBase.Light, PushDeliveryStatus.Connecting)
        compose.onNodeWithText("Not instant").assertDoesNotExist()
        capture("apple-connecting")
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("Built into Otus").assertIsDisplayed()
        capture("apple-choice")
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithText("Off, checks about every 15 minutes").assertDoesNotExist()
        capture("apple-off")
    }

    @Test
    fun unavailableDeliveryExplainsHowToRetry() {
        show(ThemeBase.Black, PushDeliveryStatus.Unavailable)
        compose.onNodeWithText("Couldn't connect. Open Mail to retry.").assertIsDisplayed()
        capture("apple-unavailable")
    }

    @Test
    fun unconfiguredBuildExplainsWhyApplePushIsUnavailable() {
        show(ThemeBase.Light, PushDeliveryStatus.Connecting, configured = false)
        compose.onNodeWithContentDescription("About Instant delivery").performClick()
        compose.onNodeWithText("Apple push is not configured in this build.").assertIsDisplayed()
        capture("apple-unconfigured")
    }

    private fun show(
        theme: ThemeBase,
        status: PushDeliveryStatus,
        configured: Boolean = true,
    ) {
        val push =
            FakeNotificationPush(if (configured) listOf(apple) else emptyList(), periodicChecks = false).apply {
                chosen = apple.takeIf { configured }
                this.status = status
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                val state = remember(push) { NotificationSettingsState(push, scope) }
                OtusTheme(ThemeChoice(base = theme).palette) {
                    NotificationsScreen(
                        state,
                        listOf(ConnectedAccount("ada", "Ada", "ada@example.org", "example.org")),
                        onBack = {},
                        onOpenOverrides = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("src/test/screenshots/notifications/$name.png")
    }
}
