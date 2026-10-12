package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The compose page against a fake Drafts folder: typing, the bar's context, leaving, and discarding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val drafts = FakeDrafts()
    private var closed = false

    private class FakeDrafts : DraftRepository {
        val saved = mutableListOf<Draft>()
        val discarded = mutableListOf<String>()

        override suspend fun identities(accountId: String) =
            listOf(MailSender("Me", "me@example.com"), MailSender("Me at work", "me@work.example"))

        override suspend fun save(draft: Draft): Result<String> {
            saved += draft
            return Result.success("d${saved.size}")
        }

        override suspend fun discard(
            accountId: String,
            emailId: String,
        ): Result<Unit> {
            discarded += emailId
            return Result.success(Unit)
        }
    }

    private fun open(saveDelayMillis: Long = 60_000) {
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
                                    drafts,
                                    scope,
                                    ComposeRequest.New(),
                                    "a1",
                                    listOf("me@example.com"),
                                    saveDelayMillis,
                                )
                            }
                        ComposeScreen(state, MailBehavior()) {
                            shown = false
                            closed = true
                        }
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
    fun theSendButtonNamesTheRecipientsAndLeavingSavesTheDraft() {
        open()
        compose.onNodeWithText("New message").assertExists()
        compose.onNodeWithContentDescription("From, Me <me@example.com>").assertExists()
        type("To", "Ada <ada@example.com>")
        type("Subject", "Lunch")
        type("Message", "Noon?")
        button("Send, to Ada").assertIsNotEnabled()
        button("Attach").assertIsNotEnabled()

        button("Show Cc and Bcc").performClick()
        type("Cc", "bob@example.com")
        button("Send, 2 people").assertIsNotEnabled()
        screenshot("compose-new")
        assertTrue(drafts.saved.isEmpty())

        button("Back").performClick()
        compose.waitUntil(TIMEOUT) { drafts.saved.isNotEmpty() }
        assertTrue(closed)
        val saved = drafts.saved.single()
        assertEquals(listOf(MailSender("Ada", "ada@example.com")), saved.to)
        assertEquals(listOf(MailSender("", "bob@example.com")), saved.cc)
        assertEquals("Lunch", saved.subject)
        assertEquals("Noon?", saved.body)
        assertEquals("me@example.com", saved.from?.address)
    }

    @Test
    fun theFromLineChoosesBetweenIdentities() {
        open()
        button("From, Me <me@example.com>").performClick()
        compose.onNodeWithText("me@work.example").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("From, Me at work <me@work.example>").assertExists()
    }

    @Test
    fun discardAsksFirstThenDeletesTheSavedCopy() {
        open(saveDelayMillis = 100)
        type("Subject", "Draft to drop")
        compose.waitUntil(TIMEOUT) { drafts.saved.isNotEmpty() }
        button("Discard").performClick()
        compose.onNodeWithText("Discard draft?").assertExists()
        compose.onNode(hasText("Discard") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { drafts.discarded.isNotEmpty() }
        assertEquals(listOf("d1"), drafts.discarded)
        assertTrue(closed)
        assertEquals(1, drafts.saved.size)
    }

    @Test
    fun anUntouchedDraftClosesWithoutAskingOrSaving() {
        open()
        button("Discard").performClick()
        compose.waitForIdle()
        assertTrue(closed)
        assertTrue(drafts.saved.isEmpty() && drafts.discarded.isEmpty())
    }

    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val file = File("build/reports/compose/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
