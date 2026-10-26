package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.mail.app.App
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDeliveryStatus
import com.otus.mail.feature.notifications.domain.PushDistributor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Notifications picks the UnifiedPush app and shows whether each account pushes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ntfy = PushDistributor("io.heckel.ntfy", "ntfy")

    private fun open(push: InstantPush?) {
        compose.runOnUiThread { compose.activity.setContent { App(services = FakeFolderServer().services(push)) } }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
    }

    @Test
    fun choosingAPushAppConnectsEachAccount() {
        val push = FakeNotificationPush(listOf(ntfy))
        open(push)
        compose.onNodeWithText("New mail every 15 minutes").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Off, checks about every 15 minutes").assertIsDisplayed()
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("ntfy").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Through ntfy").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ntfy, push.chosen)
        compose.onNodeWithText("Through ntfy").assertIsDisplayed()
        // Accounts show only when instant delivery fails for them.
        compose.onNodeWithText("Not instant").assertDoesNotExist()
    }

    @Test
    fun googleCanBeChosenWithoutInstallingAPushAppAndTurnedOffAgain() {
        val google = PushDistributor("com.otus.mail", "Google Play services", isGoogle = true)
        val push = FakeNotificationPush(listOf(google))
        open(push)
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithContentDescription("About Instant delivery").performClick()
        compose
            .onNodeWithText(
                "Choose Google Play services for instant delivery without an extra app.",
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("Built into Otus").assertIsDisplayed()
        compose.onNodeWithText("Google Play services").performClick()
        compose.waitUntil { push.chosen == google }
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("Off").performClick()
        compose.waitUntil { push.chosen == null }
        compose.onNodeWithText("Off, checks about every 15 minutes").assertIsDisplayed()
    }

    @Test
    fun unsupportedServersShowWhyTheyUseBackgroundChecks() {
        val google = PushDistributor("com.otus.mail", "Google Play services", isGoogle = true)
        val push =
            FakeNotificationPush(listOf(google)).apply {
                chosen = google
                status = PushDeliveryStatus.VapidRequired
            }
        open(push)
        compose.onNodeWithText("Delivery through Google Play services").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/notifications/google-settings.png")
        compose.onNodeWithText("Notifications").performClick()
        compose
            .onNodeWithText(
                "The server doesn't support Google delivery.",
            ).assertIsDisplayed()
        compose.onAllNodesWithText("Connecting").assertCountEquals(0)
    }

    @Test
    fun withoutAPushAppThePageSuggestsOne() {
        open(FakeNotificationPush(emptyList()))
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithContentDescription("About Instant delivery").performClick()
        compose
            .onNodeWithText(
                "For instant delivery, enable Google Play services or install a UnifiedPush app such as ntfy.",
            ).assertIsDisplayed()
        compose.onAllNodesWithText("Accounts").assertCountEquals(0)
        compose.onRoot().captureRoboImage("src/test/screenshots/notifications/google-unavailable.png")
    }

    @Test
    fun platformsWithoutPushHaveNoNotificationsPage() {
        open(null)
        compose.onAllNodesWithText("Notifications").assertCountEquals(0)
    }
}
