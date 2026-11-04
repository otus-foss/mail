package com.otus.mail

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.otus.mail.feature.inbox.domain.SavedFiles
import com.otus.mail.feature.inbox.domain.StoredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves received files to the device's Downloads through MediaStore, which needs no storage
 * permission from Android 10 on; older versions cannot save. Opening lends the downloaded file to
 * the app that shows its type through a [FileProvider], read-only, without another copy.
 */
class AndroidSavedFiles(
    private val context: Context,
) : SavedFiles {
    override suspend fun save(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean = saveAs(name, type) { it.write(bytes) }

    override suspend fun save(file: StoredFile): Boolean =
        saveAs(file.name, file.type) { output -> File(file.path).inputStream().use { it.copyTo(output) } }

    /** True once [copy] wrote the file to Downloads as [name]; a large file streams through. */
    private suspend fun saveAs(
        name: String,
        type: String,
        copy: (OutputStream) -> Unit,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return withContext(Dispatchers.IO) {
            // MediaStore numbers a name that is taken, "name (1)", but gives up after a few dozen;
            // then the file goes in under the time instead.
            write(safeName(name), type, copy) ?: write(timedName(safeName(name)), type, copy) ?: false
        }
    }

    /** True once [copy] wrote the file to Downloads as [name]; null when MediaStore could not take the name. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun write(
        name: String,
        type: String,
        copy: (OutputStream) -> Unit,
    ): Boolean? {
        val resolver = context.contentResolver
        val details =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            try {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details)
            } catch (expected: IllegalStateException) {
                null
            } catch (expected: IllegalArgumentException) {
                // MediaProvider refuses some type and name pairs, and the sender picks the type.
                null
            } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(copy) ?: throw IOException("No output for $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (expected: IOException) {
            resolver.delete(uri, null, null)
            false
        } catch (expected: IllegalStateException) {
            // Publishing the file can fail too when no free name is left.
            resolver.delete(uri, null, null)
            null
        }
    }

    override suspend fun open(file: StoredFile): Boolean {
        val uri =
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.files", File(file.path))
            } catch (expected: IllegalArgumentException) {
                // A file outside the shared folder, which a FileProvider refuses to lend.
                return false
            }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, file.type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (expected: ActivityNotFoundException) {
            false
        } catch (expected: SecurityException) {
            // Work profiles and restricted users can forbid handing the file to another app.
            false
        }
    }

    private companion object {
        /** The sender picks the name, so path separators and dot names cannot leave the folder. */
        fun safeName(name: String): String =
            name
                .replace('/', '_')
                .replace('\\', '_')
                .trim()
                .takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "attachment"
    }
}

/** [name] with the current time before its extension, such as "Contacts 20261025-162235.vcf". */
private fun timedName(name: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return "${name.substring(0, dot)} $stamp${name.substring(dot)}"
}
