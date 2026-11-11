package com.otusfoss.mail.feature.compose.presentation

import androidx.compose.runtime.snapshots.Snapshot
import com.otusfoss.mail.core.files.PickedFile
import com.otusfoss.mail.core.messages.domain.AttachmentBlob
import com.otusfoss.mail.core.messages.domain.ComposeRequest
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.feature.compose.domain.AttachmentUploads
import com.otusfoss.mail.testing.FakeDrafts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeAttachmentUndoTest {
    private val image = PickedFile("image.png", "image/png", 3) { byteArrayOf(1, 2, 3) }

    private fun TestScope.compose(
        uploads: AttachmentUploads,
        request: ComposeRequest = ComposeRequest.New(),
    ) = ComposeState(
        ComposeServices(
            FakeDrafts(),
            { _, _ -> emptyList() },
            uploads = uploads,
            files = { listOf(image) },
        ),
        backgroundScope,
        request,
        ComposeOrigin("a", listOf(ComposeAccount("a", "A"), ComposeAccount("b", "B")), emptyList()),
    )

    @Test
    fun undoRetainsImageSourcesAndCanUploadToAnotherAccount() =
        runTest {
            val calls = mutableListOf<String>()
            val uploads =
                AttachmentUploads { account, _, _ ->
                    calls += account
                    Result.success(AttachmentBlob(account, "blob"))
                }
            val first = compose(uploads)
            first.attachments.attach()
            runCurrent()
            first.discard()
            val resumed = compose(uploads, first.reopening())
            backgroundScope.launch { resumed.attachments.follow() }
            runCurrent()
            val source =
                resumed.draft.attachments
                    .single()
                    .source
            assertContentEquals(byteArrayOf(1, 2, 3), resumed.attachments.bytes(source))

            resumed.chooseFrom(SendIdentity("b", MailSender("B", "b@example.com", "b")))
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a", "b"), calls)
            assertTrue(
                resumed.draft.attachments
                    .single()
                    .uploadedTo("b"),
            )
        }

    @Test
    fun undoRestartsAnUploadThatWasPendingAtDiscard() =
        runTest {
            val held = CompletableDeferred<Unit>()
            var attempts = 0
            val uploads =
                AttachmentUploads { account, _, _ ->
                    attempts += 1
                    if (attempts == 1) held.await()
                    Result.success(AttachmentBlob(account, "blob"))
                }
            val first = compose(uploads)
            first.attachments.attach()
            runCurrent()
            first.discard()
            val resumed = compose(uploads, first.reopening())
            backgroundScope.launch { resumed.attachments.follow() }
            runCurrent()
            assertEquals(2, attempts)
            assertTrue(
                resumed.draft.attachments
                    .single()
                    .uploadedTo("a"),
            )
        }
}
