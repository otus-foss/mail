package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.core.avatar.IconSource
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.SidebarSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("read_later", true)
            .apply()
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

    @Test
    fun eachPlacesActionBarAndTheModeSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(readLater = true, actionBarMode = ActionBarMode.Fixed).copy(
                actionBar =
                    mapOf(
                        ActionBarPlace.Inbox to ActionBarLayout(listOf(MailAction.Filters), MailAction.Search),
                        ActionBarPlace.Message to ActionBarLayout(listOf(MailAction.Spam), MailAction.ReadLater),
                        ActionBarPlace.Selection to ActionBarLayout(emptyList(), MailAction.Read),
                        ActionBarPlace.Compose to ActionBarLayout(listOf(MailAction.Discard), MailAction.Send),
                        ActionBarPlace.Everywhere to ActionBarLayout(listOf(MailAction.Reply), MailAction.Trash),
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
    }

    @Test
    fun websiteIconsStayOffUntilTurnedOnAndStayOnAfterReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        assertFalse(AndroidMailBehaviorStore(context).read().websiteIcons)
        assertEquals(IconSource.Websites, AndroidMailBehaviorStore(context).read().iconSource)
        val bulwark =
            MailBehavior(websiteIcons = true, iconSource = IconSource.Bulwark, bulwarkAddress = "mail.example.net")
        AndroidMailBehaviorStore(context).write(bulwark)
        val read = AndroidMailBehaviorStore(context).read()
        assertTrue(read.websiteIcons)
        assertEquals(IconSource.Bulwark, read.iconSource)
        assertEquals("mail.example.net", read.bulwarkAddress)
    }

    @Test
    fun askDuckDuckGoTurnedOnEarlierReadsAsTheDuckDuckGoSource() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putBoolean("duckduckgo_icons", true)
        }
        assertEquals(IconSource.DuckDuckGo, AndroidMailBehaviorStore(context).read().iconSource)
    }

    @Test
    fun websiteIconsSavedOnUnderTheEarlierKeyReadAsOff() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("website_icons", true)
            .apply()
        assertFalse(AndroidMailBehaviorStore(context).read().websiteIcons)
    }
}
