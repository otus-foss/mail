package com.otusfoss.mail

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.core.designsystem.theme.typography.panelTextStyle
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MessageContent
import com.otusfoss.mail.core.messages.domain.MessageHtml
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.feature.read.domain.ConversationMessages
import com.otusfoss.mail.feature.read.domain.KnownPeople
import com.otusfoss.mail.feature.read.presentation.ReadPrivacy
import com.otusfoss.mail.feature.read.presentation.screens.MessageScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWebView

/**
 * How often an opened HTML message loads its page. Each load builds a new web view and lays the
 * mail out again, so a message that opens should load once, however its header, bar, strips, known
 * people, and conversation arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi", shadows = [CountingWebView::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadOpenLoadsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures

    @Before
    fun reset() = CountingWebView.loads.clear()

    /** Opens [row] with [pages] and each body from [content], all arriving late, as from a server. */
    private fun open(
        row: MailMessage,
        pages: List<MailMessage> = listOf(row),
        content: (MailMessage) -> MessageContent,
    ) {
        val context =
            fixtures.read(pages) {
                this.pages =
                    ConversationMessages {
                        delay(PAGES_MILLIS)
                        pages
                    }
                privacy =
                    ReadPrivacy(
                        { RemoteImages.Known },
                        allowed = { emptyList() },
                        known =
                            KnownPeople {
                                delay(KNOWN_MILLIS)
                                emptySet()
                            },
                        allowSender = {},
                    )
                loadContent = { message ->
                    flow {
                        delay(BODY_MILLIS)
                        emit(content(message))
                    }
                }
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        MessageScreen(row, fixtures.actions(), null, context, emptySet(), onClose = {})
                    }
                }
            }
        }
        compose.waitUntil(WAIT_MILLIS) { CountingWebView.loads.isNotEmpty() }
        // Later loads start off the main thread, so the clock and real time both get to pass.
        repeat(SETTLE_ROUNDS) {
            compose.mainClock.advanceTimeBy(ROUND_MILLIS)
            compose.waitForIdle()
            Thread.sleep(ROUND_SLEEP_MILLIS)
        }
        compose.waitForIdle()
    }

    private fun loadsOf(marker: String): Int = CountingWebView.loads.count { marker in it }

    @Test
    fun aNewsletterWithBlockedImagesLoadsOnce() {
        open(fixtures.digest, content = fixtures::content)
        assertEquals("Loads of the digest's page", 1, loadsOf("Your weekly digest"))
    }

    @Test
    fun anHtmlMessageWithFilesLoadsOnce() {
        open(fixtures.invoice) {
            MessageContent(
                it.body,
                truncated = false,
                html = MessageHtml("<p>The invoice for October</p>", remoteContent = false),
                attachments = fixtures.attachments,
            )
        }
        assertEquals("Loads of the invoice's page", 1, loadsOf("The invoice for October"))
    }

    @Test
    fun aReplyInAConversationLoadsOnce() {
        open(fixtures.threadRow, fixtures.thread) {
            when (it.id) {
                "g" -> MessageContent(it.body, truncated = false, html = MessageHtml(REPLY, false))
                else -> fixtures.content(it)
            }
        }
        assertEquals("Loads of the reply's page", 1, loadsOf("Thursday at 10 works"))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val BODY_MILLIS = 40L
        const val PAGES_MILLIS = 150L
        const val KNOWN_MILLIS = 250L
        const val SETTLE_ROUNDS = 12
        const val ROUND_MILLIS = 100L
        const val ROUND_SLEEP_MILLIS = 25L
        const val REPLY =
            "<div>Thursday at 10 works for me.</div><div class=\"gmail_quote\">" +
                "<div>On 10 Oct 2026, Margaret Knuth &lt;margaret@legal.example&gt; wrote:</div>" +
                "<blockquote>Two waves work, but the EU wave needs more lead time.</blockquote></div>"
    }
}

/** A web view that keeps every document it was told to load, in order. */
@Implements(WebView::class)
class CountingWebView : ShadowWebView() {
    @Implementation
    override fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String?,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        loads += data.orEmpty()
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    companion object {
        val loads = mutableListOf<String>()
    }
}
