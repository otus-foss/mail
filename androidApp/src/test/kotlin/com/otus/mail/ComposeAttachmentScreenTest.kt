package com.otus.mail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.PickedFile
import com.otus.mail.feature.inbox.domain.SentMessage
import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import com.otus.mail.feature.inbox.presentation.ComposeAccount
import com.otus.mail.feature.inbox.presentation.ComposeExits
import com.otus.mail.feature.inbox.presentation.ComposeOrigin
import com.otus.mail.feature.inbox.presentation.ComposeServices
import com.otus.mail.feature.inbox.presentation.ComposeState
import com.otus.mail.feature.inbox.presentation.screens.ComposeScreen
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.time.Instant

/**
 * The Write page with files attached: a photo shown in the message, a file uploaded, and one too
 * large for the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeAttachmentScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val photo =
        ByteArrayOutputStream()
            .also { out ->
                Bitmap
                    .createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(PHOTO_COLOR) }
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }.toByteArray()

    private val files =
        listOf(
            PickedFile("Whiteboard.png", "image/png", photo.size.toLong()) { photo },
            PickedFile("Quarterly plan.pdf", "application/pdf", 1_572_864) { ByteArray(0) },
            PickedFile("Site walkthrough.mov", "video/quicktime", 734_003_200) { ByteArray(0) },
        )

    @Test
    fun anImageShowsInTheMessageAndCanBecomeAFile() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    val scope = rememberCoroutineScope()
                    val state =
                        remember {
                            ComposeState(
                                ComposeServices(
                                    TwoAccountDrafts(),
                                    { draft, _, _ -> Result.success(SentMessage(draft.accountId, "e1", "s1")) },
                                    { _, _ -> emptyList() },
                                    uploads = { account, _, type ->
                                        if (type.startsWith("video/")) {
                                            Result.failure(UploadException(UploadFailure.TooLarge))
                                        } else {
                                            Result.success(AttachmentBlob(account, "b1"))
                                        }
                                    },
                                    files = { files },
                                ),
                                scope,
                                ComposeRequest.New(),
                                ComposeOrigin("a1", listOf(ComposeAccount("a1", "Company A")), emptyList()),
                                saveDelayMillis = 60_000,
                            )
                        }
                    ComposeScreen(state, MailBehavior(), NoExits)
                }
            }
        }
        compose.onNodeWithContentDescription("Attach").performClick()
        compose.onNodeWithText("1.5 MB").assertExists()
        compose.onNodeWithText("Too large for this server").assertExists()
        compose.onNodeWithText("In message", substring = true).assertExists()
        compose.screenshot("compose-attachments-inline")

        compose.onNodeWithText("Whiteboard.png").performClick()
        compose.onNodeWithText("Attach as file").performClick()
        compose.onNodeWithText("In message", substring = true).assertDoesNotExist()
        compose.screenshot("compose-attachments")
    }

    private companion object {
        const val PHOTO_WIDTH = 640
        const val PHOTO_HEIGHT = 400
        const val PHOTO_COLOR = 0xFF4F7A9A.toInt()
    }

    private object NoExits : ComposeExits {
        override fun close() = Unit

        override fun discarded(delete: () -> Unit) = Unit

        override fun send(
            run: suspend (Instant?) -> Result<SentMessage>,
            at: LocalDateTime?,
        ) = Unit
    }
}
