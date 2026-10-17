package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.DraftSender
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.OriginalMessage
import com.otus.mail.feature.inbox.domain.ScheduledSends
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.domain.earliestPick
import com.otus.mail.feature.inbox.presentation.ComposeAccount
import com.otus.mail.feature.inbox.presentation.ComposeExits
import com.otus.mail.feature.inbox.presentation.ComposeOrigin
import com.otus.mail.feature.inbox.presentation.ComposeServices
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Clock
import kotlin.time.Instant

/** The compose page against a fake Drafts folder: chips, the bar's context, sending, leaving, and discarding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val drafts = TwoAccountDrafts()
    private val sent = mutableListOf<Draft>()
    private val sender =
        DraftSender { draft, _, _ ->
            Result.success(SentMessage(draft.accountId, "e1", "s1")).also {
                sent +=
                    draft
            }
        }
    private var closed = false
    private var scheduledAt: LocalDateTime? = null
    private val scheduling =
        object : ScheduledSends {
            override suspend fun maxDelaySeconds(accountId: String) = THIRTY_DAYS

            override suspend fun cancel(message: SentMessage) = Result.success(Unit)
        }
    private var discarded = false

    private fun open(request: ComposeRequest = ComposeRequest.New()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    // The scope outlives the page, as the app's does, so a save on leaving finishes.
                    val scope = rememberCoroutineScope()
                    var shown by remember { mutableStateOf(true) }
                    if (shown) {
                        val state =
                            remember {
                                ComposeState(
                                    ComposeServices(drafts, sender, { _, _ -> emptyList() }, scheduling),
                                    scope,
                                    request,
                                    ComposeOrigin(
                                        "a1",
                                        listOf(ComposeAccount("a1", "Company A"), ComposeAccount("a2", "Rubylabs")),
                                        listOf("this@companya.com"),
                                        recentFrom = listOf("this@companya.com"),
                                    ),
                                    saveDelayMillis = 60_000,
                                )
                            }
                        ComposeScreen(
                            state,
                            MailBehavior(recentFrom = listOf("this@companya.com")),
                            object : ComposeExits {
                                override fun close() {
                                    shown = false
                                    closed = true
                                }

                                override fun discarded(delete: () -> Unit) {
                                    discarded = true
                                    close()
                                }

                                override fun send(
                                    run: suspend (Instant?) -> Result<SentMessage>,
                                    at: LocalDateTime?,
                                ) {
                                    scheduledAt = at
                                    scope.launch { run(at?.toInstant(TimeZone.UTC)) }
                                    close()
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun type(
        field: String,
        text: String,
    ) = compose.onNodeWithContentDescription(field).performTextInput(text)

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    @Test
    fun anEmptyMessageStartsWithADisabledSend() {
        open()
        compose.onNodeWithText("New message").assertExists()
        button("Send from this@companya.com. Change").assertExists()
        button("Send").assertIsNotEnabled()
        compose.screenshot("compose-new-empty")
    }

    @Test
    fun typedAddressesBecomeChipsAndTheSendButtonNamesThem() {
        open()
        type("To", "Grace Hopper <grace@example.com>,")
        type("Subject", "Q4 launch plan")
        type("Write your message", "Thursday works for me.")
        button("Send, to Grace Hopper").assertIsEnabled()
        compose.onNodeWithText("Grace Hopper").assertExists()
        button("Attach").assertIsNotEnabled()
        compose.screenshot("compose-new-filled")

        button("Add Cc").performClick()
        type("Cc", "bob@example.com")
        compose.onNodeWithContentDescription("Cc").performImeAction()
        button("Send, to 2 people").assertIsEnabled()

        button("Back").performClick()
        compose.waitUntil(TIMEOUT) { drafts.saved.isNotEmpty() }
        assertTrue(closed)
        val saved = drafts.saved.single()
        assertEquals(listOf(MailSender("Grace Hopper", "grace@example.com")), saved.to)
        assertEquals(listOf(MailSender("", "bob@example.com")), saved.cc)
        assertEquals("Thursday works for me.", saved.body)
    }

    @Test
    fun sendSendsTheDraftAndClosesThePage() {
        open()
        type("To", "ada@example.com")
        type("Subject", "Lunch")
        button("Send, to ada@example.com").performClick()
        compose.waitUntil(TIMEOUT) { closed }
        assertEquals("Lunch", sent.single().subject)
        assertEquals("this@companya.com", sent.single().from?.address)
        assertTrue("A sent draft is not saved again", drafts.saved.isEmpty())
    }

    @Test
    fun aMissingSubjectAsksBeforeSending() {
        open()
        type("To", "ada@example.com,")
        button("Send, to ada@example.com").performClick()
        compose.onNodeWithText("Send without a subject?").assertExists()
        compose.onNode(hasText("Add subject") and hasClickAction()).performClick()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theFromPillListsRecentAddressesThenEachAccount() {
        open()
        button("Send from this@companya.com. Change").performClick()
        compose.onNodeWithText("Recent").assertExists()
        compose.onNodeWithText("Rubylabs").assertExists()
        compose.screenshot("compose-from-dropdown")
        compose.onNodeWithText("otus@rubylabs.design").performClick()
        compose.waitForIdle()
        button("Send from otus@rubylabs.design. Change").assertExists()
    }

    @Test
    fun theMoreMenuAddsBccAndDiscards() {
        open()
        button("More options").performClick()
        compose.screenshot("compose-more-options")
        compose.onNodeWithText("Add Bcc").performClick()
        compose.onNodeWithContentDescription("Bcc").assertExists()
        button("More options").performClick()
        compose.onNodeWithText("Remove Bcc").assertExists()
        compose.onNodeWithText("Discard draft").performClick()
        compose.waitForIdle()
        assertTrue(discarded)
    }

    @Test
    fun scheduleSendPicksATimeThatShowsUntilSendSchedulesIt() {
        open(ComposeRequest.Reply(original, all = false))
        button("More options").performClick()
        compose.screenshot("compose-more-options-reply")
        compose.onNodeWithText("Schedule send").performClick()
        compose.onNodeWithText("Send later").assertExists()
        compose.onNodeWithText("Pick date and time").assertExists()
        compose.screenshot("compose-send-later")
        compose.onNodeWithText("Tomorrow morning").performClick()
        compose.waitForIdle()
        assertFalse("Choosing a time does not send", closed)
        compose.onNodeWithText("Scheduled for", substring = true).assertExists()
        compose.onNode(hasText("Change") and hasClickAction()).assertExists()
        compose.screenshot("compose-scheduled")

        compose.onNode(hasContentDescription("Send, to Grace Hopper") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { closed }
        assertEquals(8, scheduledAt?.hour)
    }

    @Test
    fun pickingATimeTodayShowsNothingBeforeNow() {
        open()
        button("More options").performClick()
        compose.onNodeWithText("Schedule send").performClick()
        compose.onNodeWithText("Pick date and time").performClick()
        compose.onNode(hasText("Go") and hasClickAction()).performClick()
        compose.onNodeWithText("Choose time").assertExists()
        compose.screenshot("compose-pick-time")
        // The date chooser starts on the first day with a time left, so its first hour is the earliest's.
        val earliest = earliestPick(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
        val hours =
            compose
                .onAllNodes(hasParent(hasContentDescription("Hour choices")))
                .fetchSemanticsNodes()
                .mapNotNull {
                    it.config
                        .getOrNull(SemanticsProperties.Text)
                        ?.firstOrNull()
                        ?.text
                        ?.toIntOrNull()
                }
        assertEquals(earliest.hour, hours.min())
    }

    @Test
    fun aForwardCarriesItsOriginalBehindAChip() {
        open(ComposeRequest.Forward(original))
        compose.onNodeWithText("Forwarded message").performClick()
        compose.onNodeWithText("Can we meet Thursday?", substring = true).assertExists()
    }

    private val original =
        OriginalMessage(
            accountId = "a1",
            emailId = "m1",
            subject = "Q4 launch plan",
            from = MailSender("Grace Hopper", "grace@example.com"),
            to = listOf(MailSender("Alex Rivera", "this@companya.com")),
            cc = emptyList(),
            sentLabel = "2026-10-11 09:12",
            body = "Can we meet Thursday?",
        )

    private companion object {
        const val TIMEOUT = 5_000L
        const val THIRTY_DAYS = 30L * 24 * 60 * 60
    }
}
