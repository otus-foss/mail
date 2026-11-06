package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.MailLocation
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The inbox keeps its place while another page is shown, as the app's pages keep their saved state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class InboxScrollRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun returningToTheInboxKeepsItsScrollPosition() {
        val messages =
            (1..40).map { n ->
                MailMessage(
                    "m$n",
                    MailSender("Sender $n", "sender$n@example.com"),
                    "Subject $n",
                    "Body $n",
                    ReceivedAt("2026-10-03", "10:30"),
                    MailLocation("Inbox", "Work", mailboxRole = MailboxRole.Inbox),
                )
            }
        var inboxShown by mutableStateOf(true)
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val pages = rememberSaveableStateHolder()
                    if (inboxShown) pages.SaveableStateProvider("inbox") { InboxScreen(messages) } else Text("Settings")
                }
            }
        }
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Subject 1"))).performScrollToIndex(30)
        compose.onNodeWithText("Subject 1").assertDoesNotExist()

        inboxShown = false
        compose.onNodeWithText("Settings").assertExists()
        inboxShown = true
        compose.waitForIdle()
        compose.onNodeWithText("Subject 1").assertDoesNotExist()
    }
}
