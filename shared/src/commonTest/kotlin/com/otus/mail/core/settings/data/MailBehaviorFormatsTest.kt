package com.otus.mail.core.settings.data

import com.otus.avatar.IconSource
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.BarButton
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
import com.otus.mail.core.settings.domain.ListOrder
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

/** Every Mail setting keeps the key and text it was saved under, so values saved by earlier builds still read. */
class MailBehaviorFormatsTest {
    private fun read(vararg saved: Pair<String, Any>) =
        KeyValueMailBehaviorStore(InMemoryKeyValueStore(saved.toMap())).read()

    @Test
    fun theListSettingsReadTheirSavedText() {
        val list =
            read(
                "inbox_pills" to "role:archive\nflagged\nfolder:acc/f1\nread_later",
                "inbox_contents" to "spam=true\ntrash=false\narchive=true\nownFolders=true",
                "filter_layout" to "people,status|flagged,attachments",
                "quick_view_modules" to "recent_files,counts",
                "mail_list" to
                    listOf(
                        "order=sender",
                        "preview=two",
                        "showSender=false",
                        "folderIcons=false",
                        "dateStyle=full-months",
                        "showThisYear=false",
                    ).joinToString("\n"),
            ).list
        assertEquals(
            listOf(
                InboxPill.Role(MailboxRole.Archive),
                InboxPill.Filter.Flagged,
                InboxPill.Folder("acc", "f1"),
                InboxPill.ReadLater,
            ),
            list.pills,
        )
        assertEquals(InboxContents(spam = true, archive = true, ownFolders = true), list.inbox)
        assertEquals(
            FilterLayout(
                sections = listOf(FilterSection.People, FilterSection.Status),
                quick = listOf(QuickFacet.Flagged, QuickFacet.Attachments),
            ),
            list.filters,
        )
        assertEquals(listOf(QuickViewModule.RecentFiles, QuickViewModule.Counts), list.quickView)
        assertEquals(
            MailListLayout(ListOrder.SenderFirst, PreviewLines.Two, false, false, DateStyle.FullMonths, false),
            list.layout,
        )
    }

    @Test
    fun noSavedPillsReadAsTheDefaultsForTheSavedReadLater() {
        assertEquals(InboxPill.defaults(readLater = true), read("read_later" to true).list.pills)
        assertEquals(InboxPill.defaults(readLater = false), read().list.pills)
    }

    @Test
    fun theReadingSettingsReadTheirSavedText() {
        val read =
            read(
                "mark_read_on_open" to false,
                "show_quotes" to true,
                "read_zen" to true,
                "read_quick_view_modules" to "counts,thread",
                "remote_images_setting" to "always",
                "allowed_senders" to "grace@example.org\n\nnews@sentry.io",
            ).read
        assertEquals(
            ReadBehavior(
                markReadOnOpen = false,
                showQuotes = true,
                zen = true,
                // The quick view shows one row now, so a saved pair keeps its first.
                quickView = listOf(ReadQuickModule.Counts),
                remoteImages = RemoteImages.Always,
                allowedSenders = listOf("grace@example.org", "news@sentry.io"),
            ),
            read,
        )
    }

    @Test
    fun theImageSettingReadsNeverFromEitherEarlierKeyAndNothingElse() {
        assertEquals(RemoteImages.Never, read("load_remote_images" to "never").read.remoteImages)
        assertEquals(RemoteImages.Never, read("remote_images" to "never").read.remoteImages)
        assertEquals(
            RemoteImages.Never,
            read("load_remote_images" to "never", "remote_images" to "known").read.remoteImages,
        )
        listOf("always", "known", "sometimes").forEach { earlier ->
            val saved = read("load_remote_images" to earlier, "remote_images" to earlier)
            assertEquals(RemoteImages.Known, saved.read.remoteImages, "Earlier value $earlier")
        }
        val chosen =
            read(
                "remote_images_setting" to "always",
                "load_remote_images" to "never",
                "remote_images" to "never",
            )
        assertEquals(RemoteImages.Always, chosen.read.remoteImages)
    }

    @Test
    fun theWritingSettingsReadTheirSavedText() {
        val write =
            read(
                "recent_from" to "me@example.org\nwork@example.com",
                "signature_in_replies" to false,
                "undo_send_seconds" to 30,
                "photo_cleaning" to "never",
            ).write
        assertEquals(WriteBehavior(listOf("me@example.org", "work@example.com"), false, 30, PhotoCleaning.Never), write)
        assertEquals(PhotoCleaning.Ask, read("photo_cleaning" to "sometimes").write.photoCleaning)
    }

    @Test
    fun theActionSettingsReadTheirSavedText() {
        val actions =
            read(
                "swipe_start_to_end" to "none",
                "swipe_end_to_start" to "archive",
                "action_bar_message" to "archive,move_to:Clients/Acme|reply_all",
                "action_bar_inbox" to "|search",
                "action_bar_mode" to "fixed",
                "action_bar_minimized" to true,
                "notification_actions" to "read,spam",
            ).actions
        assertEquals(null, actions.startToEnd)
        assertEquals(MailAction.Archive, actions.endToStart)
        assertEquals(
            ActionBarLayout(
                listOf(MailAction.Archive, BarButton.MoveTo(listOf("Clients", "Acme"))),
                MailAction.ReplyAll,
            ),
            actions.savedLayout(ActionBarPlace.Message),
        )
        assertEquals(ActionBarLayout(emptyList(), MailAction.Search), actions.savedLayout(ActionBarPlace.Inbox))
        assertEquals(ActionBarPlace.Selection.defaultLayout, actions.savedLayout(ActionBarPlace.Selection))
        assertEquals(ActionBarMode.Fixed, actions.barMode)
        assertEquals(true, actions.minimizedBar)
        assertEquals(listOf(MailAction.Read, MailAction.Spam), actions.notificationActions)
        assertEquals(emptyList<MailAction>(), read("notification_actions" to "").actions.notificationActions)
    }

    @Test
    fun unknownSwipesAndBarsReadAsTheirDefaults() {
        val actions =
            read(
                "swipe_start_to_end" to "spam",
                "swipe_end_to_start" to "snooze",
                "action_bar_selection" to "reply|archive",
                "action_bar_mode" to "bogus",
            ).actions
        assertEquals(ActionBehavior(), actions)
    }

    @Test
    fun theAvatarSettingsReadTheirSavedText() {
        val avatars =
            read(
                "load_website_icons" to true,
                "website_icon_source" to "bulwark",
                "bulwark_address" to "mail.example.net",
                "all_account_photos" to false,
            ).avatars
        assertEquals(AvatarBehavior(true, IconSource.Bulwark, "mail.example.net", false), avatars)
    }

    @Test
    fun askDuckDuckGoFromBeforeTheSourceChoiceReadsAsDuckDuckGo() {
        assertEquals(IconSource.DuckDuckGo, read("duckduckgo_icons" to true).avatars.iconSource)
        val chosen = read("duckduckgo_icons" to true, "website_icon_source" to "websites")
        assertEquals(IconSource.Websites, chosen.avatars.iconSource)
        assertEquals(false, read("website_icons" to true).avatars.websiteIcons, "The earlier key held on for everyone")
    }

    @Test
    fun theDeviceSettingsReadTheirSavedText() =
        assertEquals(
            PrivacyBehavior(screenSecurity = true, incognitoKeyboard = false),
            read("screen_security" to true, "incognito_keyboard" to false).privacy,
        )

    @Test
    fun theUpdateToastSettingReadsItsSavedFlag() {
        assertEquals(false, read("update_toasts" to false).updateToasts)
        assertEquals(true, read().updateToasts)
    }

    @Test
    fun theContactLetterSideReadsItsSavedFlag() {
        assertEquals(true, read("contact_letters_at_end" to true).lettersAtEnd)
        assertEquals(false, read().lettersAtEnd)
    }

    @Test
    fun theSidebarSettingsReadTheirSavedText() {
        val sidebar =
            read(
                "sidebar_layout" to
                    listOf(
                        "sections=folders,mailboxes",
                        "hidden=folders",
                        "pinned=inbox,role:junk,folder:clients/acme",
                        "offered=contacts,inbox,role:scheduled",
                        "sizes=wide:role:junk",
                        "mailboxes=rows",
                        "counts=total",
                        "folderColors=false",
                    ).joinToString("\n"),
                "start_page" to "role:sent",
            ).sidebar
        assertEquals(
            SidebarBehavior(
                SidebarLayout(
                    sections = listOf(SidebarSection.Folders, SidebarSection.Mailboxes),
                    hidden = setOf(SidebarSection.Folders),
                    pinned =
                        listOf(
                            SidebarPin.Inbox,
                            SidebarPin.Role(MailboxRole.Junk),
                            SidebarPin.Folder(listOf("clients", "acme")),
                        ),
                    sizes = mapOf(SidebarPin.Role(MailboxRole.Junk) to PinSize.Wide),
                    mailboxes = MailboxStyle.Rows,
                    counts = SidebarCount.Total,
                    folderColors = false,
                ),
                startPage = SidebarPin.Role(MailboxRole.Sent),
            ),
            sidebar,
        )
    }
}
