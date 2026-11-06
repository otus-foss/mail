package com.otus.mail.feature.compose.domain

import com.otus.mail.core.files.PickedFile
import com.otus.mail.core.files.UploadException
import com.otus.mail.core.files.UploadFailure
import kotlin.random.Random

/** Whether this is a JPEG, PNG, or WebP image, whose metadata [cleaned] can remove. */
val PickedFile.cleanable: Boolean get() = type.lowercase() in CLEANABLE_TYPES

/**
 * This photo without its metadata, see [withoutImageMetadata], under a random name that keeps
 * only its extension, so recipients learn neither where, when, nor with what it was taken. A
 * photo whose metadata cannot be told apart fails to read rather than going with it.
 */
fun PickedFile.cleaned(): PickedFile {
    val extension =
        name
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .takeIf { it.length in 1..MAX_EXTENSION && it.all(Char::isLetterOrDigit) }
    val random = (1..NAME_DIGITS).joinToString("") { Random.nextInt(HEX).toString(HEX) }
    return PickedFile("photo-$random" + extension?.let { ".$it" }.orEmpty(), type, size) { maxBytes ->
        withoutImageMetadata(read(maxBytes)) ?: throw UploadException(UploadFailure.Failed)
    }
}

private val CLEANABLE_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
private const val NAME_DIGITS = 8
private const val MAX_EXTENSION = 4
private const val HEX = 16
