package com.otusfoss.mail.core.files

/**
 * A picked file. The platform reader must honor the byte limit before allocating a larger buffer.
 */
class PickedFile(
    val name: String,
    val type: String,
    val size: Long,
    private val readBytes: suspend (maxBytes: Int) -> ByteArray,
) {
    suspend fun read(maxBytes: Int = MAX_ATTACHMENT_BYTES): ByteArray {
        require(maxBytes >= 0)
        if (size > maxBytes) throw UploadException(UploadFailure.TooLarge)
        val bytes = readBytes(maxBytes)
        if (bytes.size > maxBytes) throw UploadException(UploadFailure.TooLarge)
        return bytes
    }
}

/** Bounds a file held in memory on the device, even when the server accepts larger uploads. */
const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

/** Lets the user pick files to attach; empty when they pick none. */
fun interface AttachmentFiles {
    suspend fun pick(): List<PickedFile>
}

/** Why a file could not be uploaded. */
enum class UploadFailure { TooLarge, Failed }

/** The failure an upload of a picked file carries. */
class UploadException(
    val failure: UploadFailure,
    cause: Throwable? = null,
) : Exception("The file could not be uploaded: $failure", cause)
