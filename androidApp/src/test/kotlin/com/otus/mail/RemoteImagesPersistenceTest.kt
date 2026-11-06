package com.otus.mail

import android.content.Context
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.settings.domain.MailBehavior
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

    private fun readWithEarlierSetting(
        key: String,
        stored: String,
    ): MailBehavior {
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(key, stored)
            .apply()
        return AndroidMailBehaviorStore(context).read()
    }

    @Test
    fun settingsSavedUnderEarlierDefaultsAskForUnknownSenders() {
        val earlier =
            mapOf(
                "load_remote_images" to listOf("always", "known"),
                "remote_images" to listOf("known", "sometimes"),
            )
        earlier.forEach { (key, values) ->
            values.forEach { stored ->
                assertEquals(RemoteImages.Known, readWithEarlierSetting(key, stored).remoteImages)
            }
        }
        assertEquals(emptyList<String>(), readWithEarlierSetting("load_remote_images", "always").allowedSenders)
    }

    @Test
    fun neverSavedUnderAnEarlierKeyStaysNever() {
        listOf("load_remote_images", "remote_images").forEach { key ->
            assertEquals(RemoteImages.Never, readWithEarlierSetting(key, "never").remoteImages)
        }
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
