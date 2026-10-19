package com.otus.mail

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.otus.mail.feature.inbox.domain.SavedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
            val resolver = context.contentResolver
            val details =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name))
                    put(MediaStore.MediaColumns.MIME_TYPE, type)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, details) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("No output for $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                true
            } catch (expected: IOException) {
                resolver.delete(uri, null, null)
                false
            }
        }
    }

    override suspend fun open(
        name: String,
        type: String,
        bytes: ByteArray,
    ): Boolean {
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
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(view)
            true
        } catch (expected: ActivityNotFoundException) {
            false
        }
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
