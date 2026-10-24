package com.otus.mail

import com.otus.mail.feature.inbox.domain.UploadException
import com.otus.mail.feature.inbox.domain.UploadFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class AttachmentReadTest {
    @Test
    fun aFileExactlyAtTheLimitIsRead() =
        runBlocking {
            assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readAttachment(3))
        }

    @Test
    fun anOversizedStreamStopsAfterTheFirstExcessByte() =
        runBlocking {
            val input = ByteArrayInputStream(ByteArray(100))
            val failure = assertThrows(UploadException::class.java) { runBlocking { input.readAttachment(4) } }
            assertEquals(UploadFailure.TooLarge, failure.failure)
            assertEquals(95, input.available())
        }
}
