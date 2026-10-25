package com.otus.mail.feature.contacts.data

import com.otus.jscontact.Card
import com.otus.jscontact.VCard
import com.otus.mail.feature.contacts.domain.ContactFile
import com.otus.mail.feature.contacts.domain.ContactFiles
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.UploadException

/** vCard files through the platform's file picker and downloads (RFC 6350, read with [VCard]). */
internal class VCardFiles(
    private val picker: AttachmentFiles,
    private val saved: SavedFiles,
) : ContactFiles {
    override suspend fun pick(): ContactFile? {
        val file = picker.pick().firstOrNull() ?: return null
        val text =
            try {
                file.read(MAX_BYTES).decodeToString()
            } catch (expected: UploadException) {
                return ContactFile(
                    file.name,
                    emptyList(),
                    listOf(0 to "The file is larger than ${MAX_BYTES / MEGABYTE} MB."),
                )
            }
        val read = VCard.parse(text)
        return ContactFile(file.name, read.cards, read.failures.map { it.position to it.reason })
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
