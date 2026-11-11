package com.otusfoss.mail.core.settings.data

import com.otusfoss.core.avatar.IconSource
import com.otusfoss.core.storage.InMemoryKeyValueStore
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.AvatarBehavior
import com.otusfoss.mail.core.settings.domain.DateStyle
import com.otusfoss.mail.core.settings.domain.FilterLayout
import com.otusfoss.mail.core.settings.domain.FilterSection
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.domain.InboxPill
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.ListOrder
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.domain.MailboxStyle
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PinSize
import com.otusfoss.mail.core.settings.domain.PreviewLines
import com.otusfoss.mail.core.settings.domain.PrivacyBehavior
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.domain.ReadQuickModule
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.SidebarCount
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarPin
import com.otusfoss.mail.core.settings.domain.SidebarSection
import com.otusfoss.mail.core.settings.domain.WriteBehavior
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
        // A store that never saved anything has not finished the first run.
        assertEquals(MailBehavior(onboarded = false), KeyValueMailBehaviorStore(InMemoryKeyValueStore()).read())

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
                    quickView = listOf(ReadQuickModule.Counts),
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
            lettersAtEnd = true,
        )
}
