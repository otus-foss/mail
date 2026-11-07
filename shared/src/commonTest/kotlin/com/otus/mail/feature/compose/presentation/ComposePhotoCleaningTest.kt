package com.otus.mail.feature.compose.presentation

import com.otus.designsystem.mail.compose.elements.RichText
import com.otus.mail.core.files.PickedFile
import com.otus.mail.core.messages.domain.AttachmentBlob
import com.otus.mail.core.messages.domain.DraftAttachment
import com.otus.mail.core.settings.domain.PhotoCleaning
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComposePhotoCleaningTest {
    /** A JPEG with a comment, which cleaning removes. */
    private val photoBytes = byteArrayOf(-1, -40, -1, -2, 0, 4, 104, 105, -1, -39)
    private val cleanBytes = byteArrayOf(-1, -40, -1, -39)
    private val photo = PickedFile("PXL_20261012_134501.jpg", "image/jpeg", 10) { photoBytes }
    private val document = PickedFile("plan.pdf", "application/pdf", 3) { byteArrayOf(1, 2, 3) }

    private var files = listOf<DraftAttachment>()
    private val uploaded = mutableListOf<ByteArray>()

    private fun TestScope.attachments(cleaning: PhotoCleaning) =
        ComposeAttachments(
            ComposeAttachmentServices(
                files = { listOf(photo, document) },
                uploads = { account, bytes, _ ->
                    uploaded += bytes
                    Result.success(AttachmentBlob(account, "b${uploaded.size}"))
                },
                cleaning = { cleaning },
            ),
            backgroundScope,
            RichText(),
            accountId = { "a1" },
            attachments = { files },
            change = { files = it(files) },
        )

    private fun photoName() = files.first { it.type == "image/jpeg" }.name

    @Test
    fun askingOffersToCleanThePhotosAndCleansThemWhenChosen() =
        runTest {
            val attachments = attachments(PhotoCleaning.Ask)
            attachments.attach()
            runCurrent()
            assertEquals(1, attachments.cleaning.question, "Only the photo counts")
            assertTrue(files.isEmpty(), "Nothing attaches before the answer")
            attachments.cleaning.answer(true)
            runCurrent()
            assertNull(attachments.cleaning.question)
            assertTrue(Regex("photo-[0-9a-f]{8}\\.jpg").matches(photoName()), photoName())
            assertEquals("plan.pdf", files.first { it.type == "application/pdf" }.name)
            assertContentEquals(cleanBytes, uploaded.first())
        }

    @Test
    fun askingKeepsThePhotoWhenTheUserSaysSo() =
        runTest {
            val attachments = attachments(PhotoCleaning.Ask)
            attachments.attach()
            runCurrent()
            attachments.cleaning.answer(false)
            runCurrent()
            assertEquals("PXL_20261012_134501.jpg", photoName())
            assertContentEquals(photoBytes, uploaded.first())
        }

    @Test
    fun alwaysCleansWithoutAskingAndNeverKeepsWithoutAsking() =
        runTest {
            val always = attachments(PhotoCleaning.Always)
            always.attach()
            runCurrent()
            assertNull(always.cleaning.question)
            assertContentEquals(cleanBytes, uploaded.first())
            files = emptyList()
            uploaded.clear()
            val never = attachments(PhotoCleaning.Never)
            never.attach()
            runCurrent()
            assertNull(never.cleaning.question)
            assertEquals("PXL_20261012_134501.jpg", photoName())
            assertContentEquals(photoBytes, uploaded.first())
        }
}
