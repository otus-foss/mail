package com.otus.mail

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
import kotlin.time.Instant

/** The Write page with files attached: one uploaded, one too large for the server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeAttachmentScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val files =
        listOf(
            PickedFile("Quarterly plan.pdf", "application/pdf", 1_572_864) { ByteArray(0) },
            PickedFile("Site walkthrough.mov", "video/quicktime", 734_003_200) { ByteArray(0) },
        )

    @Test
    fun attachedFilesShowAsChipsUnderTheBody() {
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
        compose.screenshot("compose-attachments")
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
