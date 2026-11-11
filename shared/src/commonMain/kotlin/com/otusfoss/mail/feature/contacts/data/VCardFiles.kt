package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.VCard
import com.otusfoss.mail.core.files.AttachmentFiles
import com.otusfoss.mail.core.files.SavedFiles
import com.otusfoss.mail.core.files.UploadException
import com.otusfoss.mail.feature.contacts.domain.ContactFile
import com.otusfoss.mail.feature.contacts.domain.ContactFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * vCard files through the platform's file picker and downloads (RFC 6350, read with [VCard]). A
 * picked file of up to 10 MB is read and parsed on [parsing], off the caller's main thread.
 */
internal class VCardFiles(
    private val picker: AttachmentFiles,
    private val saved: SavedFiles,
    private val parsing: CoroutineContext = Dispatchers.Default,
) : ContactFiles {
    override suspend fun pick(): ContactFile? {
        val file = picker.pick().firstOrNull() ?: return null
        return withContext(parsing) {
            val read =
                try {
                    VCard.parse(file.read(MAX_BYTES).decodeToString())
                } catch (expected: UploadException) {
                    null
                }
            ContactFile(
                file.name,
                read?.cards.orEmpty(),
                read?.failures?.map { it.position to it.reason }
                    ?: listOf(0 to "The file is larger than ${MAX_BYTES / MEGABYTE} MB."),
            )
        }
    }

    override suspend fun save(
        name: String,
        cards: List<Card>,
    ): Boolean = saved.save(name, VCARD_TYPE, VCard.write(cards).encodeToByteArray())

    private companion object {
        const val MEGABYTE = 1024 * 1024
        const val MAX_BYTES = 10 * MEGABYTE
        const val VCARD_TYPE = "text/vcard"
    }
}
