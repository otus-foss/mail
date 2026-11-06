package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.core.messages.domain.MailSender
import com.otus.mail.core.messages.domain.OriginalMessage
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.feature.compose.domain.ThreadMessage
import com.otus.mail.feature.compose.presentation.ComposeAccount
import com.otus.mail.feature.compose.presentation.ComposeExits
import com.otus.mail.feature.compose.presentation.ComposeOrigin
import com.otus.mail.feature.compose.presentation.ComposeServices
import com.otus.mail.feature.compose.presentation.ComposeState
import com.otus.mail.feature.compose.presentation.OutgoingDraft
import com.otus.mail.feature.compose.presentation.screens.ComposeScreen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A reply's thread above the bar in its two styles, its sheet with the quote choice, and long To lines. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyThreadScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var state: ComposeState

    private val people =
        listOf(
            MailSender("Grace Hopper", "grace@example.com") to
                "Can we move the review to Thursday? I need the numbers first.",
            MailSender("Ada Lovelace", "ada@example.com") to "Works for me. I'll send the deck tonight.",
            MailSender("Margaret Knuth", "margaret@example.com") to "Adding Katherine for the numbers.",
            MailSender("Katherine Johnson", "katherine@example.com") to "Here's the first draft of the timeline.",
            MailSender("Radia Perlman", "radia@example.com") to "Kicking off Q4 planning, first thoughts below.",
        )

    private fun thread(size: Int) =
        people.take(size).mapIndexed { index, (person, text) ->
            ThreadMessage("m$index", person, LocalDate(2026, 10, 11 - index), text)
        }

    private fun open(
        request: ComposeRequest,
        size: Int,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val scope = rememberCoroutineScope()
                    state =
                        remember {
                            ComposeState(
                                ComposeServices(
                                    TwoAccountDrafts(),
                                    { _, _ -> emptyList() },
                                    threads = { _, _ -> thread(size) },
                                ),
                                scope,
                                request,
                                ComposeOrigin(
                                    "a1",
                                    listOf(ComposeAccount("a1", "Company A")),
                                    listOf("this@companya.com"),
                                ),
                                saveDelayMillis = 60_000,
                            )
                        }
                    ComposeScreen(state, ActionBehavior(), emptyList(), exits)
                }
            }
        }
        compose.waitForIdle()
    }

    private val exits =
        object : ComposeExits {
            override fun close() = Unit

            override fun discarded(delete: () -> Unit) = Unit

            override fun send(
                draft: OutgoingDraft,
                at: LocalDateTime?,
            ) = Unit
        }

    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m0",
            subject = "Q4 launch plan",
            from = people[0].first,
            to = listOf(MailSender("Alex Rivera", "this@companya.com")) + people.drop(1).map { it.first },
            cc = emptyList(),
            sentLabel = "2026-10-11 09:12",
            body = "Can we move the review to Thursday?\n\nOn 2026-10-10, Ada wrote:\n> Works for me.",
        )

    @Test
    fun aReplyToOneMessageShowsItAsARow() {
        open(ComposeRequest.Reply(original, all = false), 1)
        compose.onNodeWithText("11 Oct").assertExists()
        compose.onNode(hasContentDescription("Send, to Grace Hopper") and hasClickAction()).assertIsEnabled()
        compose.screenshot("compose/reply_thread_one")
    }

    @Test
    fun upToThreeMessagesShowAsRows() {
        open(ComposeRequest.Reply(original, all = false), 3)
        compose.onNodeWithText("Margaret Knuth").assertExists()
        compose.screenshot("compose/reply_thread_three")
    }

    @Test
    fun aLongThreadFoldsIntoOneRowAndALongToLineIntoMore() {
        open(ComposeRequest.Reply(original, all = true), 5)
        compose.onNodeWithText("5 earlier messages").assertExists()
        compose.onNodeWithText("Latest: Grace Hopper, 11 Oct").assertExists()
        compose.onNodeWithText("+3 more").assertExists()
        compose.screenshot("compose/reply_thread_folded")
        compose.onNodeWithText("+3 more").performClick()
        compose.onNodeWithText("Radia Perlman").assertExists()
        compose.screenshot("compose/reply_to_expanded")
    }

    @Test
    fun theSheetListsTheThreadAndSetsHowMuchToQuote() {
        open(ComposeRequest.Reply(original, all = true), 5)
        compose.onNodeWithText("5 earlier messages").performClick()
        compose.onNodeWithText("Earlier in this thread").assertExists()
        compose.screenshot("compose/reply_thread_sheet")
        compose.onNode(hasText("Latest only") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(
            "On 2026-10-11 09:12, Grace Hopper wrote:\n> Can we move the review to Thursday?",
            state.draft.quote,
        )
        compose.onNode(hasText("None") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals("", state.draft.quote)
    }
}
