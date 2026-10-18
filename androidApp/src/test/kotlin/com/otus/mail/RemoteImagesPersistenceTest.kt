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

    @Test
    fun settingsSavedBeforeThePrivacyPageLoadImagesFromPeopleTheUserKnows() {
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putString("remote_images", "sometimes")
        }
        val read = AndroidMailBehaviorStore(context).read()
        assertEquals(RemoteImages.Known, read.remoteImages)
        assertEquals(emptyList<String>(), read.allowedSenders)
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
