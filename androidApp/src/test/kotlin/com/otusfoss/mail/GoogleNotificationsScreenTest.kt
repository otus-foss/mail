package com.otusfoss.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.core.designsystem.theme.colors.ThemeBase
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import com.otusfoss.mail.feature.notifications.domain.PushDistributor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoogleNotificationsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val google = PushDistributor("com.otusfoss.mail", "Google Play services", isGoogle = true)

    @Test
    fun activeGoogleDeliveryUsesTheSelectedTheme() {
        listOf(ThemeBase.Otus, ThemeBase.Light, ThemeBase.Black).forEach { theme ->
            show(theme, PushDeliveryStatus.Active)
            compose.onNodeWithText("Not instant").assertDoesNotExist()
            capture("google-${theme.name.lowercase()}")
        }
    }

    @Test
    fun unsupportedServerExplainsTheFallback() {
        show(ThemeBase.Light, PushDeliveryStatus.VapidRequired)
        compose.onNodeWithText("The server doesn't support Google delivery.").assertIsDisplayed()
        capture("google-unsupported")
    }

    @Test
    fun failedRegistrationExplainsTheRetry() {
        show(ThemeBase.Black, PushDeliveryStatus.RegistrationFailed)
        compose.onNodeWithText("Push registration failed.").assertIsDisplayed()
        capture("google-failed")
    }

    @Test
    fun connectingAndChoosingGoogleNeedNoExtraApp() {
        show(ThemeBase.Otus, PushDeliveryStatus.Connecting)
        compose.onNodeWithText("Not instant").assertDoesNotExist()
        capture("google-connecting")
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("Built into Otus").assertIsDisplayed()
        capture("google-choice")
        compose.onNodeWithText("Off").performClick()
        compose.onNodeWithText("Off, checks about every 15 minutes").assertIsDisplayed()
        capture("google-off")
    }

    private fun show(
        theme: ThemeBase,
        status: PushDeliveryStatus,
    ) {
        val push =
            FakeNotificationPush(listOf(google)).apply {
                chosen = google
                this.status = status
            }
        compose.showNotifications(push, theme)
    }

    private fun capture(name: String) = compose.screenshot("notifications/$name")
}
