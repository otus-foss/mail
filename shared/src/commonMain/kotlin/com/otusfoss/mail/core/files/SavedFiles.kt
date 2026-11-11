package com.otusfoss.mail.core.files

/** The platform's file handling for attachments and other files the app makes. */
interface SavedFiles {
    /** Saves [bytes] to the device's downloads as [name]; false when it failed. */
    suspend fun save(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean

    /** Copies a downloaded [file] to the device's downloads; false when it failed. */
    suspend fun save(file: StoredFile): Boolean

    /** Opens a downloaded [file] in an app that shows it; false when none can. */
    suspend fun open(file: StoredFile): Boolean
}

/**
 * A downloaded attachment on the device. [path] is where its bytes lie, for the platform's file
 * handling; [kept] says whether the user keeps it offline.
 */
data class StoredFile(
    val path: String,
    val name: String,
    val type: String,
    val size: Long,
    val kept: Boolean,
)
