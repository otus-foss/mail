package com.otus.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import com.otus.mail.feature.inbox.domain.AttachmentFiles
import com.otus.mail.feature.inbox.domain.PickedFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks files of any type with the system document picker of the activity in front. Each file is
 * read again whenever it is uploaded, which the picker allows while the process lives.
 */
class AndroidAttachmentFiles(
    private val context: Context,
) : AttachmentFiles {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<List<Uri>>? = null

    /** Registers the picker for [activity]; call it from `onCreate`, before the activity starts. */
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(OpenMultipleDocuments()) { pending?.complete(it) }
    }

    override suspend fun pick(): List<PickedFile> {
        val picker = launcher ?: return emptyList()
        val result = CompletableDeferred<List<Uri>>().also { pending = it }
        picker.launch(arrayOf("*/*"))
        return withContext(Dispatchers.IO) { result.await().mapNotNull(::picked) }
    }

    private fun picked(uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        val (name, size) =
            resolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { row -> row.takeIf { it.moveToFirst() }?.let { it.getString(0).orEmpty() to it.getLong(1) } }
                ?: return null
        return PickedFile(
            name.ifBlank { uri.lastPathSegment ?: "file" },
            resolver.getType(uri) ?: GENERIC_TYPE,
            size,
        ) {
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("The file is no longer available")
            }
        }
    }

    private companion object {
        const val GENERIC_TYPE = "application/octet-stream"
    }
}
