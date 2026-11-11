package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.core.messages.domain.original
import com.otusfoss.mail.core.messages.presentation.MessageActions
import com.otusfoss.mail.core.messages.presentation.MessageMarks
import com.otusfoss.mail.core.messages.presentation.MessageMoves
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.feature.inbox.presentation.components.ReadView
import com.otusfoss.mail.feature.inbox.presentation.screens.InboxScreen
import com.otusfoss.mail.feature.read.presentation.screens.MessageScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** New mail, reply, and forward start drafts, and the reply button names who it goes to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val requests = mutableListOf<ComposeRequest>()
    private val message =
        MailMessage(
            "work:m1",
            MailSender("Grace Hopper", "grace@example.org"),
            "Compilers",
            "Hello",
            ReceivedAt("2026-10-04", "09:30"),
            MailLocation("Inbox", "Work", "work", "me@example.org", mailboxRole = MailboxRole.Inbox),
            emailId = "m1",
            to = listOf(MailSender("Me", "ME@example.org"), MailSender("Ada", "ada@example.org")),
            cc = listOf(MailSender("Grace", "grace@example.org"), MailSender("Alan", "alan@example.org")),
        )

    private fun start(
        actions: ActionBehavior = ActionBehavior(),
        shown: MailMessage = message,
    ) {
        val messageActions =
            MessageActions(
                moves = MessageMoves(targets = { emptyList() }, onMove = { _, _ -> }, suggested = { null }) { null },
                marks = MessageMarks(can = { _, _ -> false }, set = { _, _, _ -> }),
                ruleForSender = { null },
                readLater = { null },
                saved = { actions },
                markReadOnOpen = { true },
                cleanup = null,
            )
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    InboxScreen(
                        listOf(shown),
                        context =
                            inboxContext {
                                onCompose = { requests += it }
                                readView =
                                    ReadView { opened ->
                                        val read = readContext { onCompose = { requests += it } }
                                        MessageScreen(
                                            opened.message,
                                            messageActions,
                                            null,
                                            read,
                                            emptySet(),
                                            opened.close,
                                        )
                                    }
                            },
                        messageActions = messageActions,
                    )
                }
            }
        }
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    @Test
    fun newMailStartsABlankDraft() {
        start()
        button("New mail").performClick()
        assertEquals(listOf(ComposeRequest.New()), requests)
    }

    @Test
    fun replyNamesTheSenderAndAnswersThem() {
        // Sent to the user alone, so the big button replies to the sender rather than to all.
        val toMe = message.copy(to = listOf(MailSender("Me", "me@example.org")), cc = emptyList())
        start(shown = toMe)
        compose.onNodeWithText("Compilers").performClick()
        button("Reply, to Grace Hopper").performClick()
        assertEquals(listOf(ComposeRequest.Reply(toMe.original(), all = false)), requests)
    }

    @Test
    fun replyAllCountsEveryoneElseOnceAndForwardIsInTheMenu() {
        val replyAll = ActionBarLayout(listOf(MailAction.Archive), MailAction.ReplyAll)
        start(ActionBehavior(bars = ActionBehavior().bars + (ActionBarPlace.Message to replyAll)))
        compose.onNodeWithText("Compilers").performClick()
        // Grace, Ada, and Alan; the account's own address is left out.
        button("Reply all, to 3 people").performClick()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Forward").performClick()
        assertEquals(
            listOf(ComposeRequest.Reply(message.original(), all = true), ComposeRequest.Forward(message.original())),
            requests,
        )
    }
}
