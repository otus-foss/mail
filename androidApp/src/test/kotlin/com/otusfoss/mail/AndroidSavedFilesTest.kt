package com.otusfoss.mail

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.otusfoss.mail.core.files.StoredFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Saving and opening an attachment use the type of its name, never an app package's type. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSavedFilesTest {
    private val app = ApplicationProvider.getApplicationContext<OtusMailApplication>()

    private fun opened(
        name: String,
        type: String,
    ): Intent {
        val file = File(app.filesDir, "attachments/ada/$name/$name").apply { parentFile?.mkdirs() }
        file.writeBytes(byteArrayOf(1))
        assertTrue(runBlocking { AndroidSavedFiles(app).open(StoredFile(file.path, name, type, 1, kept = false)) })
        return shadowOf(app).nextStartedActivity
    }

    // One test opens every file: FileProvider caches its root folder for the process, and
    // Robolectric gives each test a new files folder, so a second test's file would fall outside it.
    @Test
    fun noAttachmentOpensTheInstaller() {
        val labelled = opened("invoice.pdf", "application/vnd.android.package-archive")
        assertEquals(Intent.ACTION_VIEW, labelled.action)
        assertTrue(labelled.type != "application/vnd.android.package-archive")
        assertEquals("application/octet-stream", opened("setup.apk", "application/vnd.android.package-archive").type)
    }

    @Test
    fun executableTypesOpenAsPlainBytes() {
        assertEquals("application/octet-stream", viewType("tool", "Application/X-MSDownload; name=tool"))
        assertEquals("application/pdf", viewType("invoice", "application/pdf"))
    }

    private fun savedType(
        name: String,
        type: String,
    ): String? {
        val media = Robolectric.setupContentProvider(RecordedInserts::class.java, MediaStore.AUTHORITY)
        runBlocking { AndroidSavedFiles(app).save(name, type, byteArrayOf(1)) }
        // The provider refuses every name, so the timed retry arrives too and must carry the same type.
        return media.inserted
            .map { it.getAsString(MediaStore.MediaColumns.MIME_TYPE) }
            .distinct()
            .single()
    }

    @Test
    fun aSavedFileKeepsTheTypeOfItsName() {
        assertEquals("application/pdf", savedType("invoice.pdf", "application/vnd.android.package-archive"))
        assertEquals("image/jpeg", savedType("photo.jpg", "image/jpeg"))
    }

    @Test
    fun aSavedProgramWithoutAKnownExtensionIsPlainBytes() {
        assertEquals("application/octet-stream", savedType("setup", "application/vnd.android.package-archive"))
    }
}

/** Keeps what the app asks Downloads to insert and refuses the rest. */
class RecordedInserts : ContentProvider() {
    val inserted = mutableListOf<ContentValues>()

    override fun onCreate() = true

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        values?.let(inserted::add)
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
