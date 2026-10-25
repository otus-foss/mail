package com.otus.mail

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.otus.mail.feature.inbox.domain.SavedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves received files to the device's Downloads through MediaStore, which needs no storage
 * permission from Android 10 on; older versions cannot save. Opening writes the file to the app's
 * cache and lends it to the app that shows its type through a [FileProvider].
 */
class AndroidSavedFiles(
    private val context: Context,
) : SavedFiles {
    override suspend fun save(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return withContext(Dispatchers.IO) {
            // MediaStore numbers a name that is taken, "name (1)", but gives up after a few dozen;
            // then the file goes in under the time instead.
            write(safeName(name), type, bytes) ?: write(timedName(safeName(name)), type, bytes) ?: false
        }
    }

    /** True once [bytes] are in Downloads as [name]; null when MediaStore could not take the name. */
    private fun write(
        name: String,
        type: String,
        bytes: ByteArray,
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
            } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("No output for $uri")
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

    override suspend fun open(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean {
        val uri = sharedCopy(name, bytes) ?: return false
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(view)
            true
        } catch (expected: ActivityNotFoundException) {
            false
        } catch (expected: SecurityException) {
            // Work profiles and restricted users can forbid handing the file to another app.
            false
        }
    }

    /**
     * [bytes] written to the app's cache under [name], as a content address other apps may read;
     * null when the disk is full or the share is refused, which must not crash the app.
     */
    private suspend fun sharedCopy(
        name: String,
        bytes: ByteArray,
    ): Uri? =
        try {
            val file =
                withContext(Dispatchers.IO) {
                    val opened = File(context.cacheDir, OPENED_DIRECTORY)
                    pruneOpened(opened)
                    // A folder per file keeps its own name, so two files with one name never clash.
                    File(opened, System.nanoTime().toString())
                        .apply { mkdirs() }
                        .resolve(safeName(name))
                        .apply { writeBytes(bytes) }
                }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (expected: IOException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }

    /**
     * Deletes files opened more than a day ago. The app that shows a file reads it soon after, so
     * older copies only take space.
     */
    private fun pruneOpened(opened: File) {
        val cutoff = System.currentTimeMillis() - OPENED_KEEP_MILLIS
        opened
            .listFiles()
            .orEmpty()
            .filter { it.lastModified() < cutoff }
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        /** Matches the `cache-path` in `res/xml/shared_files.xml`. */
        const val OPENED_DIRECTORY = "opened"
        const val OPENED_KEEP_MILLIS = 24L * 60 * 60 * 1000

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
