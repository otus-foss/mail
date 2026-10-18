package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.otus.mail.feature.inbox.domain.AttachmentBlob
import com.otus.mail.feature.inbox.domain.DraftAttachment
import com.otus.mail.feature.inbox.domain.PickedFile
import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposeAttachmentsTest {
    private val file = PickedFile("plan.pdf", "application/pdf", 3) { byteArrayOf(1, 2, 3) }

    /** A draft's account and attachments, and every upload the server received, by account. */
    private class Draft {
        var accountId by mutableStateOf("a1")
        var files by mutableStateOf<List<DraftAttachment>>(emptyList())
        val uploads = mutableListOf<String>()
        var failNext: UploadFailure? = null
    }

    private fun TestScope.attachments(draft: Draft) =
        ComposeAttachments(
            files = { listOf(file) },
            uploads = { account, _, _ ->
                draft.uploads += account
                draft.failNext?.let {
                    draft.failNext = null
                    Result.failure(UploadException(it))
                } ?: Result.success(AttachmentBlob(account, "b${draft.uploads.size}"))
            },
            scope = backgroundScope,
            accountId = { draft.accountId },
            attachments = { draft.files },
            change = { draft.files = it(draft.files) },
        )

    @Test
    fun aPickedFileUploadsToTheDraftsAccount() =
        runTest {
            val draft = Draft()
            attachments(draft).attach()
            runCurrent()
            assertEquals(listOf("a1"), draft.uploads)
            assertEquals(AttachmentBlob("a1", "b1"), draft.files.single().blob)
        }

    @Test
    fun aFailedUploadSaysWhyAndTriesAgainOnRetry() =
        runTest {
            val draft = Draft().apply { failNext = UploadFailure.Failed }
            val attachments = attachments(draft)
            attachments.attach()
            runCurrent()
            val id = draft.files.single().id
            assertEquals(UploadFailure.Failed, attachments.failures[id])
            assertFalse(attachments.uploading(draft.files.single()))

            attachments.retry(id)
            runCurrent()
            assertTrue(draft.files.single().uploadedTo("a1"))
            assertTrue(attachments.failures.isEmpty())
        }

    @Test
    fun movingTheDraftToAnotherAccountUploadsTheFileThere() =
        runTest {
            val draft = Draft()
            val attachments = attachments(draft)
            backgroundScope.launch { attachments.followAccount() }
            attachments.attach()
            runCurrent()
            draft.accountId = "a2"
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("a1", "a2"), draft.uploads)
            assertTrue(draft.files.single().uploadedTo("a2"))
        }

    @Test
    fun removingAFileDropsItFromTheDraft() =
        runTest {
            val draft = Draft()
            val attachments = attachments(draft)
            attachments.attach()
            runCurrent()
            attachments.remove(draft.files.single().id)
            assertTrue(draft.files.isEmpty())
        }
}
