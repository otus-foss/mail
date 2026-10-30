package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailAttributes
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.InboxStatus
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxCompactPanelTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val filters = InboxFilterState()

    @Test
    fun aCompactPanelStaysCompactWhenAFilterIsApplied() {
        show()
        val expanded = promptTop()
        scrollList { swipeUp() }
        val compact = promptTop()
        assertNotEquals("Scrolling down makes the panel compact", expanded, compact)

        compose.runOnIdle { filters.update(InboxFilters(status = ReadStatus.Unread)) }
        compose.waitForIdle()
        assertEquals("A new filter keeps the compact panel", compact, promptTop())

        scrollList { swipeDown() }
        assertEquals("Scrolling back up expands the panel", expanded, promptTop())
    }

    private fun show() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    InboxScreen(
                        messages,
                        status = InboxStatus(connected = true),
                        context = InboxContext(filters = filters),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun scrollList(swipe: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        compose.onNodeWithText("Message 10").performTouchInput(swipe)
        compose.waitForIdle()
    }

    private fun promptTop() =
        compose
            .onNodeWithText("Tap to search")
            .fetchSemanticsNode()
            .boundsInRoot.top
}

private val messages =
    (1..40).map { number ->
        MailMessage(
            "message-$number",
            MailSender("Sender $number", "sender$number@example.com"),
            "Message $number",
            "Body $number",
            ReceivedAt("2026-10-03", "10:30"),
            MailLocation("Inbox", "Work"),
            MailAttributes(unread = number % 2 == 0),
        )
    }
