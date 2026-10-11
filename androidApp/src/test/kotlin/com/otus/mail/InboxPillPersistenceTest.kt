package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailBehavior
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InboxPillPersistenceTest {
    @Test
    fun existingReadLaterSettingsGetTheirDefaultPills() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putBoolean("read_later", true)
        }
        val restored = AndroidMailBehaviorStore(context).read()
        assertEquals(InboxPill.defaults(readLater = true), restored.pills)
    }

    @Test
    fun reorderedPillsAndAnEmptyRowSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(
                readLater = true,
                pills = listOf(InboxPill.Role("archive"), InboxPill.Filter.Flagged, InboxPill.ReadLater),
                combinedFolders = true,
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
        AndroidMailBehaviorStore(context).write(saved.copy(pills = emptyList()))
        assertEquals(saved.copy(pills = emptyList()), AndroidMailBehaviorStore(context).read())
    }
}
