package com.otusfoss.mail

import android.content.Context
import androidx.core.content.edit
import com.otusfoss.core.avatar.IconSource
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.AvatarBehavior
import com.otusfoss.mail.core.settings.domain.InboxPill
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailboxStyle
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.SidebarCount
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarSection
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
        assertEquals(InboxPill.defaults(readLater = true), restored.list.pills)
    }

    @Test
    fun reorderedPillsAndAnEmptyRowSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(
                readLater = true,
                list =
                    ListBehavior(
                        listOf(InboxPill.Role(MailboxRole.Archive), InboxPill.Filter.Flagged, InboxPill.ReadLater),
                    ),
                sidebar =
                    SidebarBehavior(
                        SidebarLayout(
                            sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes),
                            hidden = setOf(SidebarSection.Mailboxes),
                            mailboxes = MailboxStyle.Rows,
                            counts = SidebarCount.Total,
                        ),
                    ),
            )
        AndroidMailBehaviorStore(context).write(saved)
        assertEquals(saved, AndroidMailBehaviorStore(context).read())
        AndroidMailBehaviorStore(context).write(saved.copy(list = saved.list.copy(pills = emptyList())))
        assertEquals(saved.copy(list = saved.list.copy(pills = emptyList())), AndroidMailBehaviorStore(context).read())
    }

    @Test
    fun eachPlacesActionBarAndTheModeSurviveReopeningTheStore() {
        val context = RuntimeEnvironment.getApplication()
        val saved =
            MailBehavior(
                readLater = true,
                actions =
                    ActionBehavior(
                        barMode = ActionBarMode.Fixed,
                        bars =
                            mapOf(
                                ActionBarPlace.Inbox to ActionBarLayout(listOf(MailAction.Filters), MailAction.Search),
                                ActionBarPlace.Message to
                                    ActionBarLayout(
                                        listOf(MailAction.Spam),
                                        MailAction.ReadLater,
                                    ),
                                ActionBarPlace.Selection to ActionBarLayout(emptyList(), MailAction.Read),
                                ActionBarPlace.Compose to ActionBarLayout(listOf(MailAction.Discard), MailAction.Send),
                                ActionBarPlace.Everywhere to
                                    ActionBarLayout(
                                        listOf(MailAction.Reply),
                                        MailAction.Trash,
                                    ),
                            ),
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
        assertFalse(AndroidMailBehaviorStore(context).read().avatars.websiteIcons)
        assertEquals(IconSource.Websites, AndroidMailBehaviorStore(context).read().avatars.iconSource)
        val bulwark =
            MailBehavior(
                avatars =
                    AvatarBehavior(
                        websiteIcons = true,
                        iconSource = IconSource.Bulwark,
                        bulwarkAddress = "mail.example.net",
                    ),
            )
        AndroidMailBehaviorStore(context).write(bulwark)
        val read = AndroidMailBehaviorStore(context).read()
        assertTrue(read.avatars.websiteIcons)
        assertEquals(IconSource.Bulwark, read.avatars.iconSource)
        assertEquals("mail.example.net", read.avatars.bulwarkAddress)
    }

    @Test
    fun askDuckDuckGoTurnedOnEarlierReadsAsTheDuckDuckGoSource() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("otus_mail_behavior", Context.MODE_PRIVATE).edit {
            clear()
            putBoolean("duckduckgo_icons", true)
        }
        assertEquals(IconSource.DuckDuckGo, AndroidMailBehaviorStore(context).read().avatars.iconSource)
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
        assertFalse(AndroidMailBehaviorStore(context).read().avatars.websiteIcons)
    }
}
