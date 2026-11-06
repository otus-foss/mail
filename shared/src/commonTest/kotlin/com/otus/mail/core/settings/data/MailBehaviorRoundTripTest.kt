package com.otus.mail.core.settings.data

import com.otus.avatar.IconSource
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.core.settings.domain.AvatarBehavior
import com.otus.mail.core.settings.domain.DateStyle
import com.otus.mail.core.settings.domain.FilterLayout
import com.otus.mail.core.settings.domain.FilterSection
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.domain.ListBehavior
import com.otus.mail.core.settings.domain.ListOrder
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.MailboxStyle
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.core.settings.domain.PinSize
import com.otus.mail.core.settings.domain.PreviewLines
import com.otus.mail.core.settings.domain.PrivacyBehavior
import com.otus.mail.core.settings.domain.QuickViewModule
import com.otus.mail.core.settings.domain.ReadBehavior
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.domain.SidebarBehavior
import com.otus.mail.core.settings.domain.SidebarCount
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.domain.SidebarSection
import com.otus.mail.core.settings.domain.WriteBehavior
import com.otus.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the current build saves reads back unchanged. */
class MailBehaviorRoundTripTest {
    @Test
    fun everyChangedSettingSurvivesReopeningTheStore() {
        val values = InMemoryKeyValueStore()
        KeyValueMailBehaviorStore(values).write(changed)
        assertEquals(changed, KeyValueMailBehaviorStore(values).read())
    }

    @Test
    fun nothingSavedReadsAsTheDefaults() =
        assertEquals(MailBehavior(), KeyValueMailBehaviorStore(InMemoryKeyValueStore()).read())

    private val changed =
        MailBehavior(
            readLater = true,
            list =
                ListBehavior(
                    pills =
                        listOf(
                            InboxPill.ReadLater,
                            InboxPill.Role(MailboxRole.Trash),
                            InboxPill.Folder("acc", "f1"),
                        ),
                    inbox = InboxContents(spam = true, trash = true, archive = true, ownFolders = true),
                    filters =
                        FilterLayout(
                            listOf(FilterSection.Rows, FilterSection.Sort),
                            listOf(QuickFacet.ReadLater),
                        ),
                    quickView = listOf(QuickViewModule.Addresses),
                    layout =
                        MailListLayout(
                            ListOrder.SenderFirst,
                            PreviewLines.One,
                            false,
                            false,
                            DateStyle.Full,
                            false,
                        ),
                ),
            read =
                ReadBehavior(
                    markReadOnOpen = false,
                    showQuotes = true,
                    zen = true,
                    quickView = listOf(ReadQuickModule.Sender),
                    remoteImages = RemoteImages.Never,
                    allowedSenders = listOf("grace@example.org"),
                ),
            write = WriteBehavior(listOf("me@example.org"), signatureInReplies = false, 0, PhotoCleaning.Always),
            actions =
                ActionBehavior(
                    startToEnd = MailAction.ReadLater,
                    endToStart = null,
                    bars =
                        ActionBarPlace.entries.associateWith { it.defaultLayout } +
                            (ActionBarPlace.Message to ActionBarLayout(listOf(MailAction.Spam), MailAction.ReadLater)),
                    barMode = ActionBarMode.Fixed,
                    minimizedBar = true,
                    notificationActions = listOf(MailAction.Reply),
                ),
            avatars = AvatarBehavior(true, IconSource.DuckDuckGo, "bulwark.example.net", allAccountPhotos = false),
            privacy = PrivacyBehavior(screenSecurity = true, incognitoKeyboard = false),
            sidebar =
                SidebarBehavior(
                    SidebarLayout(
                        sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes),
                        pinned = listOf(SidebarPin.Contacts, SidebarPin.Inbox, SidebarPin.ReadLater),
                        sizes = mapOf(SidebarPin.ReadLater to PinSize.Full),
                        mailboxes = MailboxStyle.Rows,
                        counts = SidebarCount.None,
                        folderColors = false,
                    ),
                    startPage = SidebarPin.Folder(listOf("clients", "a/b testing")),
                ),
        )
}
