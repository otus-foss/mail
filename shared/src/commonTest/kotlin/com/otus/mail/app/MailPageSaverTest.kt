package com.otus.mail.app

import androidx.compose.runtime.saveable.SaverScope
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ComposeRequest
import com.otus.mail.feature.contacts.domain.CardKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MailPageSaverTest {
    @Test
    fun everyPageComesBackAsItWasSaved() {
        assertTrue(pages.all(::sampled))
        pages.forEach { page ->
            val expected = if (page is MailPage.Compose) page.parent else page
            assertEquals(expected, MailPageSaver.restore(saved(page)), "$page")
        }
    }

    @Test
    fun aSignatureEditorSavedByAnOlderBuildComesBack() {
        val old = MailPage.SignatureEditor("acc", "id-1").toString()
        assertEquals(MailPage.SignatureEditor("acc", "id-1"), MailPageSaver.restore(listOf(old)))
    }

    private fun saved(page: MailPage): List<String> = with(MailPageSaver) { SaverScope { true }.save(page) }.orEmpty()

    /** Fails to compile until a new page has a sample in [pages]. */
    private fun sampled(page: MailPage): Boolean =
        when (page) {
            MailPage.Inbox, MailPage.Settings, MailPage.EmailAccounts, MailPage.Appearance, MailPage.Notifications,
            MailPage.NotificationOverrides, MailPage.Behavior, MailPage.SidebarSettings, MailPage.FilterPanelSettings,
            MailPage.PinnedSettings, MailPage.ThemeSettings, MailPage.MailListSettings, MailPage.ActionBarSettings,
            MailPage.Privacy, MailPage.AllowedSenders, MailPage.Folders, MailPage.Signatures, MailPage.ReadLater,
            MailPage.Conversations, MailPage.Contacts, is MailPage.Compose, is MailPage.SignatureEditor,
            is MailPage.FolderDetail, is MailPage.Folder, is MailPage.Combined, is MailPage.Shared, is MailPage.Contact,
            is MailPage.ContactEditor,
            -> pages.any { it::class == page::class }
        }

    private val pages =
        listOf(
            MailPage.Inbox,
            MailPage.Settings,
            MailPage.EmailAccounts,
            MailPage.Appearance,
            MailPage.Notifications,
            MailPage.NotificationOverrides,
            MailPage.Behavior,
            MailPage.SidebarSettings,
            MailPage.FilterPanelSettings,
            MailPage.PinnedSettings,
            MailPage.ThemeSettings,
            MailPage.MailListSettings,
            MailPage.ActionBarSettings,
            MailPage.Privacy,
            MailPage.AllowedSenders,
            MailPage.Folders,
            MailPage.Signatures,
            MailPage.ReadLater,
            MailPage.Conversations,
            MailPage.Contacts,
            MailPage.Compose(ComposeRequest.New(), parent = MailPage.Folder("acc", "f1")),
            MailPage.SignatureEditor("acc", "id-1"),
            MailPage.FolderDetail("acc", "f1"),
            MailPage.FolderDetail("acc", "f1", sharedIn = "shared"),
            MailPage.Folder("acc", "f1"),
            MailPage.Combined(MailboxRole.Junk),
            MailPage.Shared(listOf("work", "clients")),
            MailPage.Contact(CardKey("login", "acc", "card")),
            MailPage.ContactEditor(),
            MailPage.ContactEditor(CardKey("login", "acc", "card")),
        )
}
