package com.otusfoss.mail.feature.contacts.data

import com.otusfoss.mail.core.files.PickedFile
import com.otusfoss.mail.core.files.SavedFiles
import com.otusfoss.mail.core.files.StoredFile
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A picked vCard file is read and parsed off the main thread (issue #282). */
class VCardFilesTest {
    @Test
    fun aPickedFileIsReadAndParsedOnTheParsingContext() =
        runTest {
            val text = listOf("BEGIN:VCARD", "VERSION:4.0", "FN:Ada Lovelace", "END:VCARD").joinToString("\r\n")
            var readOn: String? = null
            val file =
                PickedFile("ada.vcf", "text/vcard", text.length.toLong()) {
                    readOn = currentCoroutineContext()[CoroutineName]?.name
                    text.encodeToByteArray()
                }
            val picked = VCardFiles({ listOf(file) }, NoSavedFiles, CoroutineName(PARSING)).pick()
            assertEquals(PARSING, readOn, "The file must not be read and parsed on the caller's main thread")
            assertEquals(listOf("Ada Lovelace"), picked?.cards?.map { it.name?.full })
        }

    private object NoSavedFiles : SavedFiles {
        override suspend fun save(
            name: String,
            type: String,
            bytes: ByteArray,
        ) = false

        override suspend fun save(file: StoredFile) = false

        override suspend fun open(file: StoredFile) = false
    }

    private companion object {
        const val PARSING = "vcard parsing"
    }
}
