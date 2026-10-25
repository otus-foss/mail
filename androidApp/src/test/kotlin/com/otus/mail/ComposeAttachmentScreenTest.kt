package com.otus.mail

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.ComposeRequest
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.PhotoCleaning
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

    private val photo = png(PHOTO_WIDTH, PHOTO_HEIGHT)

    private val files =
        listOf(
            PickedFile("Whiteboard.png", "image/png", photo.size.toLong()) { photo },
            PickedFile("Quarterly plan.pdf", "application/pdf", 1_572_864) { ByteArray(0) },
            PickedFile("Site walkthrough.mov", "video/quicktime", 734_003_200) { ByteArray(0) },
        )

    @Test
    fun anImageShowsInTheMessageAndCanBecomeAFile() {
        open(files)
        attach("Whiteboard.png")
        compose.onNodeWithText("1.5 MB").assertExists()
        compose.onNodeWithText("File is too large").assertExists()
        compose.onNodeWithText("Whiteboard.png").assertDoesNotExist()
        compose.onNodeWithContentDescription("Whiteboard.png").performClick()
        compose.onNodeWithText("Full").assertIsSelected()
        compose.screenshot("compose-attachments-inline")

        val handle = compose.onNodeWithContentDescription("Resize image")
        handle.performTouchInput {
            down(center)
            moveBy(Offset(-DRAG_TO_LARGE, 0f))
        }
        // The pill under the handle and the bar's segment both name the snapped size.
        compose.onAllNodesWithText("Large").assertCountEquals(2)
        compose.screenshot("compose-attachments-drag")
        handle.performTouchInput { up() }

        compose.onNodeWithContentDescription("Image options").performClick()
        compose.screenshot("compose-attachments-image-options")
        compose.onNodeWithText("Send as file").performClick()
        compose.onNodeWithText("Whiteboard.png").assertExists()
        compose.screenshot("compose-attachments")
    }

    @Test
    fun aTallImageShowsTheGuideLabelsUnderItWhileItsTopIsScrolledAway() {
        val tall = png(PHOTO_HEIGHT, PHOTO_WIDTH * 3)
        open(listOf(PickedFile("Screenshot.png", "image/png", tall.size.toLong()) { tall }))
        attach("Screenshot.png")
        compose.onNodeWithContentDescription("Screenshot.png").performClick()
        // Scroll until the handle at the image's bottom shows, which takes its top out of view.
        compose.onNodeWithContentDescription("Resize image").performScrollTo()
        // The handle now sits at the screen's edge, under the bar; lift it above the bar.
        compose.onNodeWithContentDescription("Screenshot.png").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - ABOVE_THE_BAR)
        }
        val handle = compose.onNodeWithContentDescription("Resize image")
        handle.performTouchInput {
            down(center)
            moveBy(Offset(-DRAG_TO_LARGE, 0f))
        }
        compose.screenshot("compose-attachments-drag-tall")
        handle.performTouchInput { up() }
    }

    @Test
    fun aPhotoAttachesWithoutItsDetailsUnlessTheUserKeepsThem() {
        open(files.take(1), PhotoCleaning.Ask)
        compose.onNodeWithContentDescription("Attach").performClick()
        compose.onNodeWithText("Attach this photo").assertExists()
        compose.onNodeWithText("Remove details").performClick()
        compose.waitUntil(DECODE_MILLIS) {
            compose
                .onAllNodes(hasContentDescription("photo-", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Whiteboard.png").assertDoesNotExist()
    }

    /** Attaches the picked files and waits for the image [name], which decodes off the first frames. */
    private fun attach(name: String) {
        compose.onNodeWithContentDescription("Attach").performClick()
        compose.waitUntil(DECODE_MILLIS) {
            compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun open(
        files: List<PickedFile>,
        cleaning: PhotoCleaning = PhotoCleaning.Never,
    ) {
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
                                ComposeOrigin(
                                    "a1",
                                    listOf(ComposeAccount("a1", "Company A")),
                                    emptyList(),
                                    photoCleaning = { cleaning },
                                ),
                                saveDelayMillis = 60_000,
                            )
                        }
                    ComposeScreen(state, MailBehavior(), NoExits)
                }
            }
        }
    }

    private fun png(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                Bitmap
                    .createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(PHOTO_COLOR) }
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }.toByteArray()

    private companion object {
        const val PHOTO_WIDTH = 640
        const val PHOTO_HEIGHT = 400
        const val PHOTO_COLOR = 0xFF4F7A9A.toInt()

        const val DECODE_MILLIS = 5_000L

        /** Pixels the page moves up so content at the screen's bottom clears the bar. */
        const val ABOVE_THE_BAR = 500f

        /** Pixels left from full width to Large on this screen. */
        const val DRAG_TO_LARGE = 150f
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
