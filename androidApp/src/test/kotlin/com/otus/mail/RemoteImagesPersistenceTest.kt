package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.RemoteImages
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteImagesPersistenceTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun readWithEarlierSetting(stored: String): MailBehavior {
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putString("remote_images", stored)
        }
        return AndroidMailBehaviorStore(context).read()
    }

    @Test
    fun settingsSavedBeforeImagesLoadedByDefaultLoadThemAlways() {
        listOf("known", "sometimes").forEach { stored ->
            assertEquals(RemoteImages.Always, readWithEarlierSetting(stored).remoteImages)
        }
        assertEquals(emptyList<String>(), readWithEarlierSetting("known").allowedSenders)
    }

    @Test
    fun neverSavedBeforeImagesLoadedByDefaultStaysNever() {
        assertEquals(RemoteImages.Never, readWithEarlierSetting("never").remoteImages)
    }

    @Test
    fun theImageSettingAndAllowedSendersSurviveReopeningTheStore() {
        val saved =
            MailBehavior(
                remoteImages = RemoteImages.Never,
                allowedSenders = listOf("grace@example.org", "news@sentry.io"),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }
}
