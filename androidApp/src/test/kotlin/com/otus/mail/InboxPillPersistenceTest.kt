package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection
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
                sidebar =
                    SidebarLayout(
                        sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes),
                        hidden = setOf(SidebarSection.Mailboxes),
                        mailboxes = MailboxStyle.Rows,
                        counts = SidebarCount.Total,
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
        AndroidMailBehaviorStore(context).write(saved.copy(pills = emptyList()))
        assertEquals(saved.copy(pills = emptyList()), AndroidMailBehaviorStore(context).read())
    }
}
