package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.Draft
import com.otus.mail.feature.inbox.domain.DraftRepository
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.SignatureImage
import com.otus.mail.feature.inbox.domain.SignatureParts
import com.otus.mail.feature.inbox.presentation.ComposeAccount
import com.otus.mail.feature.inbox.presentation.SignatureEditorState
import com.otus.mail.feature.inbox.presentation.SignaturesState
import com.otus.mail.feature.inbox.presentation.screens.SignatureEditorScreen
import com.otus.mail.feature.inbox.presentation.screens.SignaturesScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.io.encoding.Base64

/** Settings › Signatures, the signature editor, and copying signatures, against fake identities. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val saved = mutableListOf<Pair<String, Signature>>()

    private val alex =
        identity(
            "this@companya.com",
            "a1",
            Signature(
                SignatureParts("<p>Alex Rivera</p><p>Product lead · Company A</p>", listOf(logo())).html(),
                "Alex Rivera\nProduct lead · Company A",
            ),
        )
    private val sales =
        identity("sales@companya.com", "a1", Signature("<p>Company A Sales</p>", "Company A Sales\n+49 30 1234567"))
    private val support = identity("support@companya.com", "a1")
    private val otus = identity("otus@rubylabs.design", "a2", Signature("<p>Alex</p>", "Alex\nRubylabs"))
    private val hello = identity("hello@rubylabs.design", "a2")

    private val drafts =
        object : DraftRepository {
            override suspend fun identities(accountId: String) =
                listOf(alex, sales, support, otus, hello).filter { it.accountId == accountId }

            override suspend fun save(draft: Draft) = Result.success("d1")

            override suspend fun discard(
                accountId: String,
                emailId: String,
            ) = Result.success(Unit)
        }

    private fun show(content: @Composable (SignaturesState) -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val scope = rememberCoroutineScope()
                    val state =
                        androidx.compose.runtime.remember {
                            SignaturesState(drafts, { identity, signature ->
                                saved += identity.id to signature
                                Result.success(Unit)
                            }, null, scope) {
                                listOf(ComposeAccount("a1", "Company A"), ComposeAccount("a2", "Rubylabs"))
                            }
                        }
                    content(state)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theListShowsEachAddressWithTheStartOfItsSignature() {
        show { SignaturesScreen(it, inReplies = true, onInRepliesChange = {}, onBack = {}, onEdit = {}) }
        compose.onNodeWithText("Add to replies").assertExists()
        compose.onNodeWithText("Alex Rivera · Product lead · Company A").assertExists()
        compose.onNodeWithText("Rubylabs").assertExists()
        compose.screenshot("signatures-list")
    }

    @Test
    fun theEditorShowsTheTextAndASelectedImageWithItsSize() {
        show { state ->
            androidx.compose.runtime.LaunchedEffect(Unit) { state.load() }
            val editor = androidx.compose.runtime.remember { SignatureEditorState(state, alex) }
            SignatureEditorScreen(state, editor, onBack = {}, onCopied = {})
        }
        // The image decodes off the first frame.
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithContentDescription("Signature image").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Signature image").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Medium").assertExists()
        compose.screenshot("signature-editor")
        compose.onNodeWithText("Medium").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Image options").performClick()
        compose.screenshot("signature-image-size")
        compose.onNodeWithText("Remove image").assertExists()
    }

    @Test
    fun copyingFromAnotherAddressReplacesThisSignature() {
        var copied = false
        show { state ->
            androidx.compose.runtime.LaunchedEffect(Unit) { state.load() }
            val editor = androidx.compose.runtime.remember { SignatureEditorState(state, alex) }
            SignatureEditorScreen(state, editor, onBack = {}, onCopied = { copied = true })
        }
        compose.onNodeWithContentDescription("Copy signature").performClick()
        compose.onNodeWithText("Replaces the signature of this@companya.com.").assertExists()
        compose.onNode(hasText("Copy") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("otus@rubylabs.design").performClick()
        compose.screenshot("signature-copy")
        compose.onNode(hasText("Copy") and hasClickAction()).assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT) { copied }
        assertEquals("this@companya.com" to Signature("<p>Alex</p>", "Alex\nRubylabs"), saved.first())
    }

    private fun identity(
        address: String,
        account: String,
        signature: Signature = Signature(),
    ) = SendIdentity(address, MailSender("Alex Rivera", address, account), signature.text, signature.html)

    /** A 150 × 50 badge, like the boards' Company A logo. */
    private fun logo(): SignatureImage {
        val bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRoundRect(RectF(0f, 0f, 300f, 100f), 16f, 16f, Paint().apply { color = 0xFFE9EAEB.toInt() })
            drawRoundRect(RectF(30f, 25f, 80f, 75f), 10f, 10f, Paint().apply { color = 0xFF4F6F93.toInt() })
        }
        return SignatureImage("image/png", Base64.encode(bitmap.pngBytes()), 150)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
