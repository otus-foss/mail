package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import com.otus.mail.feature.notifications.domain.InstantPush
import com.otus.mail.feature.notifications.domain.PushDistributor
import com.otus.push.PushStatus
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

    private class FakePush(
        val installed: List<PushDistributor>,
    ) : InstantPush {
        var chosen: PushDistributor? = null
        var status = PushStatus.Waiting

        override suspend fun distributors() = installed

        override suspend fun current() = chosen

        override suspend fun use(distributor: PushDistributor?) {
            chosen = distributor
        }

        override suspend fun statuses() = if (chosen == null) emptyMap() else mapOf("ada" to status)

        override suspend fun forget(loginId: String) = Unit
    }

    private fun open(push: InstantPush?) {
        compose.runOnUiThread { compose.activity.setContent { App(services = FakeFolderServer().services(push)) } }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
    }

    @Test
    fun choosingAPushAppConnectsEachAccount() {
        val push = FakePush(listOf(ntfy))
        open(push)
        compose.onNodeWithText("New mail every 15 minutes").assertIsDisplayed()
        compose.onNodeWithText("Notifications").performClick()
        compose.onNodeWithText("Off, checks every 15 minutes").assertIsDisplayed()
        compose.onNodeWithText("Instant delivery").performClick()
        compose.onNodeWithText("ntfy").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Connecting").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ntfy, push.chosen)
        compose.onNodeWithText("Through ntfy, within seconds").assertIsDisplayed()
        compose.onNodeWithText("Ada").assertIsDisplayed()
    }

    @Test
    fun withoutAPushAppThePageSuggestsOne() {
        open(FakePush(emptyList()))
        compose.onNodeWithText("Notifications").performClick()
        compose
            .onNodeWithText(
                "To get new mail within seconds, install a UnifiedPush app such as ntfy, then choose it here.",
            ).assertIsDisplayed()
        compose.onAllNodesWithText("Accounts").assertCountEquals(0)
    }

    @Test
    fun platformsWithoutPushHaveNoNotificationsPage() {
        open(null)
        compose.onAllNodesWithText("Notifications").assertCountEquals(0)
    }
}
