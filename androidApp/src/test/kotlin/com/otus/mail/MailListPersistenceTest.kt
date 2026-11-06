package com.otus.mail

import android.content.Context
import com.otus.mail.core.settings.domain.DateStyle
import com.otus.mail.core.settings.domain.ListOrder
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.PreviewLines
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MailListPersistenceTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun settingsSavedBeforeTheMailListPageReadItsDefaults() {
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("read_later", true)
            .apply()
        assertEquals(MailListLayout(), AndroidMailBehaviorStore(context).read().list)
    }

    @Test
    fun theMailListLayoutSurvivesReopeningTheStore() {
        val saved =
            MailBehavior(
                list =
                    MailListLayout(
                        order = ListOrder.SenderFirst,
                        preview = PreviewLines.One,
                        showSender = false,
                        folderIcons = false,
                        dateStyle = DateStyle.Full,
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }
}
