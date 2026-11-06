package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.mail.compose.elements.RichText
import com.otus.mail.core.files.PickedFile
import com.otus.mail.core.files.UploadFailure
import com.otus.mail.core.messages.domain.AttachmentBlob
import com.otus.mail.core.messages.domain.DraftAttachment
import com.otus.mail.feature.compose.domain.AttachmentUploads
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeAttachmentLimitsTest {
    private fun TestScope.attachments(
        files: List<PickedFile>,
        uploads: AttachmentUploads,
    ): ComposeAttachments {
        var attached = emptyList<DraftAttachment>()
        return ComposeAttachments(
            ComposeAttachmentServices({ files }, uploads),
            backgroundScope,
            RichText(),
            { "a" },
            { attached },
            { attached = it(attached) },
        )
    }

    @Test
    fun advertisedOversizeFailsBeforeOpeningTheFile() =
        runTest {
            var read = false
            val uploads =
                object : AttachmentUploads {
                    override suspend fun maxBytes(accountId: String) = 4

                    override suspend fun upload(
                        accountId: String,
                        bytes: ByteArray,
                        type: String,
                    ): Result<AttachmentBlob> = error("Must not upload")
                }
            val state =
                attachments(
                    listOf(
                        PickedFile("large", "application/octet-stream", 5) {
                            read = true
                            byteArrayOf()
                        },
                    ),
                    uploads,
                )
            state.attach()
            runCurrent()
            assertTrue(!read)
            assertEquals(listOf(UploadFailure.TooLarge), state.failures.values.toList())
        }

    @Test
    fun onlyOneFileIsReadWhileAnUploadIsPending() =
        runTest {
            val held = CompletableDeferred<Unit>()
            var reads = 0
            var uploads = 0
            val file =
                PickedFile("file", "application/octet-stream", 1) {
                    reads += 1
                    byteArrayOf(1)
                }
            val state =
                attachments(listOf(file, file)) { account, _, _ ->
                    uploads += 1
                    if (uploads == 1) held.await()
                    Result.success(AttachmentBlob(account, "b"))
                }
            state.attach()
            runCurrent()
            assertEquals(1, reads)
            held.complete(Unit)
            runCurrent()
            assertEquals(2, reads)
        }
}
