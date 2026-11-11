package com.otusfoss.mail

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.otusfoss.mail.core.files.StoredFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Opening a downloaded attachment hands it to another app by its own type, never as an app package. */
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

    @Test
    fun aFileTheSenderLabelsAsAnAppDoesNotOpenTheInstaller() {
        val intent = opened("invoice.pdf", "application/vnd.android.package-archive")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.type != "application/vnd.android.package-archive")
    }

    @Test
    fun anAppPackageOpensAsPlainBytes() {
        assertEquals("application/octet-stream", opened("setup.apk", "application/vnd.android.package-archive").type)
        assertEquals("application/octet-stream", viewType("tool", "Application/X-MSDownload; name=tool"))
        assertEquals("application/pdf", viewType("invoice", "application/pdf"))
    }
}
