package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
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
class InboxPullToRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var refreshes = 0
    private var loading by mutableStateOf(false)
    private val haptics = mutableListOf<HapticFeedbackType>()

    @Test
    fun longPullOnEmptyInboxRefreshesOnce() {
        show(emptyList())
        pull("Your inbox is empty", distance = 0.9f)
        assertEquals(1, refreshes)
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics)
    }

    @Test
    fun shortPullDoesNotRefresh() {
        show(emptyList())
        pull("Your inbox is empty", distance = 0.08f)
        assertEquals(0, refreshes)
        assertEquals(emptyList<HapticFeedbackType>(), haptics)
    }

    @Test
    fun heldPullShowsArcAndRefreshesOnlyOnRelease() {
        show(emptyList())
        val height = compose.activity.window.decorView.height
        val node = compose.onNodeWithText("Your inbox is empty")
        node.performTouchInput {
            down(center)
            repeat(PULL_STEPS) { moveBy(Offset(0f, height * 0.25f / PULL_STEPS)) }
        }
        screenshot("pull-to-refresh-held")
        assertEquals(0, refreshes)
        node.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, refreshes)
    }

    @Test
    fun longPullOnShortMessageListRefreshesAndShowsSpinnerWhileLoading() {
        show(
            listOf(
                MailMessage(
                    "alex",
                    MailSender("Alex", "alex@example.com"),
                    "Design review",
                    "The prototype is ready",
                    ReceivedAt("2026-10-03", "10:30"),
                    MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
                ),
            ),
        )
        pull("Design review", distance = 0.9f)
        assertEquals(1, refreshes)
        screenshot("pull-to-refresh-loading")
    }

    private fun show(messages: List<MailMessage>) {
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalHapticFeedback provides RecordingHaptics(haptics)) {
                    OtusTheme {
                        InboxScreen(
                            messages,
                            status = InboxStatus(connected = true, loading = loading),
                            context = inboxContext(),
                            onRefresh = {
                                refreshes++
                                loading = true
                            },
                        )
                    }
                }
            }
        }
    }

    /** Drags down from the node by [distance] of the window height. */
    private fun pull(
        text: String,
        distance: Float,
    ) {
        val height = compose.activity.window.decorView.height
        compose.onNodeWithText(text).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + height * distance, durationMillis = 400)
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) = compose.windowBitmap().savePng("build/reports/inbox/$name.png")
}

private const val PULL_STEPS = 10

private class RecordingHaptics(
    private val performed: MutableList<HapticFeedbackType>,
) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        performed += hapticFeedbackType
    }
}
