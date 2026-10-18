package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.PreviewLines
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
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putBoolean("read_later", true)
        }
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
