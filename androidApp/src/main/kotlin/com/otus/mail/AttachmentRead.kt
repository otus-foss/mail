package com.otus.mail

import com.otus.mail.core.files.UploadException
import com.otus.mail.core.files.UploadFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most the limit plus one byte, even when a provider omits or understates its size. */
internal suspend fun InputStream.readAttachment(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer, 0, minOf(buffer.size.toLong(), maxBytes.toLong() - output.size() + 1).toInt())
        if (count < 0) return output.toByteArray()
        if (count > maxBytes - output.size()) throw UploadException(UploadFailure.TooLarge)
        output.write(buffer, 0, count)
    }
}
