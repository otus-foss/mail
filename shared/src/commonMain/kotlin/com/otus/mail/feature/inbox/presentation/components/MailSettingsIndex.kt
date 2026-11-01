package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_bookmark
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_filter_list
import com.otus.designsystem.resources.symbol_format_list_bulleted
import com.otus.designsystem.resources.symbol_forum
import com.otus.designsystem.resources.symbol_image
import com.otus.designsystem.resources.symbol_inbox
import com.otus.designsystem.resources.symbol_mark_email_read
import com.otus.designsystem.resources.symbol_menu
import com.otus.designsystem.resources.symbol_photo_camera
import com.otus.designsystem.resources.symbol_schedule_send
import com.otus.designsystem.resources.symbol_verified_user
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.designsystem.resources.symbol_visibility_off
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.DateStyle
import com.otus.mail.feature.inbox.domain.DevicePrivacy
import com.otus.mail.feature.inbox.domain.ListOrder
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailListLayout
import com.otus.mail.feature.inbox.domain.MailboxStyle
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.PreviewLines
import com.otus.mail.feature.inbox.domain.RemoteImages
import com.otus.mail.feature.inbox.domain.SidebarCount
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.UNDO_SEND_CHOICES
import com.otus.mail.feature.inbox.presentation.FolderPillOption
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import com.otus.mail.feature.inbox.presentation.inboxSummary
import com.otus.mail.feature.inbox.presentation.label
import com.otus.mail.feature.inbox.presentation.screens.icon
import com.otus.mail.feature.inbox.presentation.screens.title
import com.otus.mail.feature.inbox.presentation.sectionSummary
import com.otus.mail.feature.inbox.presentation.summary
import com.otus.mail.feature.inbox.presentation.title
import com.otus.settings.presentation.SettingsIndexScope

// What settings search finds on Mail's pages. Each function mirrors the screen it names.

/** The Mail rows of the Appearance page: the Sidebar and Mail list pages. */
fun SettingsIndexScope.mailLookIndex(
    behavior: MailBehavior,
    openSidebar: (String?) -> Unit,
    openPinned: (String?) -> Unit,
    openMailList: (String?) -> Unit,
) {
    page("Sidebar", openSidebar, Res.drawable.symbol_menu, value = behavior.sidebar.sectionSummary) {
        sidebarIndex(behavior.sidebar, openPinned)
    }
    page("Mail list", openMailList, Res.drawable.symbol_format_list_bulleted, value = behavior.list.summary) {
        mailListIndex(behavior.list)
    }
}

/** Mirrors SidebarSettingsScreen. */
private fun SettingsIndexScope.sidebarIndex(
    layout: SidebarLayout,
    openPinned: (String?) -> Unit,
) {
    section("Sections") {
        layout.sections.forEach { toggle(it.title, it !in layout.hidden, it.icon) }
    }
    section("Pinned") {
        page("Mailboxes and folders", openPinned)
        setting("Show as", layout.mailboxes.label, MailboxStyle.entries.map { it.label })
    }
    section("Layout") {
        setting("Counts", layout.counts.label, SidebarCount.entries.map { it.label })
        toggle("Folder colors", layout.folderColors)
    }
}

/** Mirrors MailListSettingsScreen. */
private fun SettingsIndexScope.mailListIndex(list: MailListLayout) {
    section("Rows") {
        setting("Order", list.order.label, ListOrder.entries.map { it.label })
        toggle("Show sender", list.showSender)
        setting("Preview", list.preview.label, PreviewLines.entries.map { it.label })
        toggle("Folder icons", list.folderIcons)
    }
    section("Dates") {
        setting("Style", list.dateStyle.label, DateStyle.entries.map { it.label })
        toggle("Show this year", list.showThisYear)
    }
}

/** Mirrors the Mail behavior page; [rtl] names the swipes as the screen does. */
fun SettingsIndexScope.mailBehaviorIndex(
    state: MailBehaviorState,
    folderPills: List<FolderPillOption>,
    rtl: Boolean,
    openActionBar: (String?) -> Unit,
) {
    val behavior = state.behavior
    section("Inbox") {
        setting("Inbox shows", inboxSummary(behavior.inbox), icon = Res.drawable.symbol_inbox)
        setting(
            "Pills",
            shownCount(InboxPills(behavior.pills, folderPills).shown.size),
            icon = Res.drawable.symbol_filter_list,
        )
        setting("Filter panel", shownCount(behavior.filters.sections.size), icon = Res.drawable.symbol_filter_list)
        setting("Quick filters", shownCount(behavior.filters.quick.size), icon = Res.drawable.symbol_filter_list)
        toggle("Read later", behavior.readLater, Res.drawable.symbol_bookmark)
    }
    section("Actions") {
        val swipes = (listOf(null) + behavior.swipeChoices).map { it.swipeLabel }
        SwipeSide.entries.forEach { setting(it.title(rtl), it.of(behavior).swipeLabel, swipes, it.icon(rtl)) }
        page("Action bar", openActionBar, Res.drawable.symbol_view_agenda) {
            setting("Layout", behavior.actionBarMode.label, ActionBarMode.entries.map { it.label })
            toggle("Minimize", behavior.minimizedBar)
        }
    }
    section("Reading") {
        toggle("Mark as read when opened", behavior.markReadOnOpen, Res.drawable.symbol_mark_email_read)
        toggle("Show quoted text", behavior.showQuotes, Res.drawable.symbol_forum)
    }
    section("Sending") {
        setting(
            "Undo send",
            behavior.undoSendSeconds.undoLabel,
            UNDO_SEND_CHOICES.map { it.undoLabel },
            Res.drawable.symbol_schedule_send,
        )
    }
}

/** Mirrors PrivacyScreen, with the device protections [device] offers. */
fun SettingsIndexScope.privacyIndex(
    behavior: MailBehavior,
    device: DevicePrivacy,
    openAllowed: (String?) -> Unit,
) {
    section("Load remote images") {
        setting(
            "Load images",
            behavior.remoteImages.title,
            RemoteImages.entries.map { it.title },
            Res.drawable.symbol_image,
        )
        val count = behavior.allowedSenders.size
        page(
            "Allowed senders",
            openAllowed,
            Res.drawable.symbol_verified_user,
            value = if (count == 1) "1 sender" else "$count senders",
        )
    }
    section("Photos you attach") {
        setting(
            "Photo details",
            behavior.photoCleaning.title,
            PhotoCleaning.entries.map { it.title },
            Res.drawable.symbol_photo_camera,
        )
    }
    section("Avatars") {
        toggle("Website icons", behavior.websiteIcons)
        setting("Icons from", behavior.iconSource.title)
        toggle("Photos from all accounts", behavior.allAccountPhotos)
    }
    section("This device") {
        if (device.screen) toggle("Screen security", behavior.screenSecurity, Res.drawable.symbol_visibility_off)
        if (device.keyboard) toggle("Incognito keyboard", behavior.incognitoKeyboard, Res.drawable.symbol_edit)
    }
}
