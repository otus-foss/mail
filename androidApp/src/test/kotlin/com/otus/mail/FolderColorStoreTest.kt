package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.folders.domain.FolderColors
import com.otus.mail.feature.folders.domain.FolderKey
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.readColors
import com.otus.mail.feature.folders.domain.writeColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderColorStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun colorsSurviveReopeningTheStore() {
        // Login ids hold a space and the session URL's colons.
        val ada = MailFolder("ada https://mail.example.org/jmap/session", "M1", "Clients")
        val bob = MailFolder("bob https://mail.example.com/jmap/session", "M1", "Clients")
        val saved = FolderColors().with(listOf(ada), "Red").with(listOf(bob), "Blue")
        AndroidFolderColorStore(context).writeColors(saved)
        assertEquals(saved, AndroidFolderColorStore(context).readColors())
    }

    @Test
    fun unreadableValuesReadAsTheDefault() {
        context.getSharedPreferences("otus_mail_folder_colors", Context.MODE_PRIVATE).edit {
            clear()
            putInt("M1:ada", 3)
            putString("no separator", "Red")
            putString("M2:ada", "")
            putString("M3:ada", "Green")
        }
        assertEquals(mapOf(FolderKey("ada", "M3") to "Green"), AndroidFolderColorStore(context).readColors().names)
    }
}
