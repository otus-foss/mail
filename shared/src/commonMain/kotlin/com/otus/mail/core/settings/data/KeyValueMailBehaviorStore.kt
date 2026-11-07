package com.otus.mail.core.settings.data

import com.otus.avatar.IconSource
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.domain.RemoteImages
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.core.settings.domain.AvatarBehavior
import com.otus.mail.core.settings.domain.FilterLayout
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.domain.ListBehavior
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailBehaviorStore
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.core.settings.domain.PrivacyBehavior
import com.otus.mail.core.settings.domain.QuickViewModule
import com.otus.mail.core.settings.domain.ReadBehavior
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.domain.SidebarBehavior
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.domain.WriteBehavior
import com.otus.mail.core.settings.domain.decodeNotificationActions
import com.otus.mail.core.settings.domain.encodeNotificationActions
import com.otus.mail.core.storage.TextCodec
import com.otus.storage.KeyValueEditor
import com.otus.storage.KeyValueStore
import com.otus.storage.decoded
import com.otus.storage.flag
import com.otus.storage.int

/** Keeps [MailBehavior] in [values], one key per setting; each group reads and writes its own keys. */
class KeyValueMailBehaviorStore(
    private val values: KeyValueStore,
) : MailBehaviorStore {
    override fun read(): MailBehavior =
        with(values) {
            val readLater = flag(READ_LATER, MailBehavior().readLater)
            MailBehavior(
                readLater = readLater,
                list = listBehavior(readLater),
                read = readBehavior(),
                write = writeBehavior(),
                actions = actionBehavior(),
                avatars = avatarBehavior(),
                privacy = privacyBehavior(),
                sidebar = sidebarBehavior(),
                updateToasts = flag(UPDATE_TOASTS, MailBehavior().updateToasts),
            )
        }

    override fun write(behavior: MailBehavior) {
        values.edit {
            put(READ_LATER, behavior.readLater)
            put(behavior.list)
            put(behavior.read)
            put(behavior.write)
            put(behavior.actions)
            put(behavior.avatars)
            put(behavior.privacy)
            put(behavior.sidebar)
            put(UPDATE_TOASTS, behavior.updateToasts)
        }
    }
}

private fun KeyValueStore.listBehavior(readLater: Boolean): ListBehavior {
    val defaults = ListBehavior()
    return ListBehavior(
        pills = decoded(PILLS, InboxPill::decode) ?: InboxPill.defaults(readLater),
        inbox = decoded(INBOX, defaults.inbox, InboxContents::decode),
        filters = decoded(FILTERS, defaults.filters, FilterLayout::decode),
        quickView = decoded(QUICK_VIEW, defaults.quickView, QuickViewModule::decode),
        layout = decoded(LIST, defaults.layout, MailListLayout::decode),
    )
}

private fun KeyValueEditor.put(list: ListBehavior) {
    put(PILLS, InboxPill.encode(list.pills))
    put(INBOX, InboxContents.encode(list.inbox))
    put(FILTERS, FilterLayout.encode(list.filters))
    put(QUICK_VIEW, QuickViewModule.encode(list.quickView))
    put(LIST, MailListLayout.encode(list.layout))
}

private fun KeyValueStore.readBehavior(): ReadBehavior {
    val defaults = ReadBehavior()
    return ReadBehavior(
        markReadOnOpen = flag(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
        showQuotes = flag(SHOW_QUOTES, defaults.showQuotes),
        zen = flag(ZEN_READ, defaults.zen),
        quickView = decoded(READ_QUICK_VIEW, defaults.quickView, ReadQuickModule::decode),
        remoteImages =
            decodeRemoteImages(string(REMOTE_IMAGES), string(ALWAYS_REMOTE_IMAGES), string(EARLIER_REMOTE_IMAGES))
                ?: defaults.remoteImages,
        allowedSenders = string(ALLOWED_SENDERS)?.let(TextCodec.Lines.decode).orEmpty(),
    )
}

private fun KeyValueEditor.put(read: ReadBehavior) {
    put(MARK_READ_ON_OPEN, read.markReadOnOpen)
    put(SHOW_QUOTES, read.showQuotes)
    put(ZEN_READ, read.zen)
    put(READ_QUICK_VIEW, ReadQuickModule.encode(read.quickView))
    put(REMOTE_IMAGES, read.remoteImages.storageValue)
    put(ALLOWED_SENDERS, TextCodec.Lines.encode(read.allowedSenders))
}

private fun KeyValueStore.writeBehavior(): WriteBehavior {
    val defaults = WriteBehavior()
    return WriteBehavior(
        recentFrom = string(RECENT_FROM)?.let(TextCodec.Lines.decode).orEmpty(),
        signatureInReplies = flag(SIGNATURE_IN_REPLIES, defaults.signatureInReplies),
        undoSendSeconds = int(UNDO_SEND_SECONDS, defaults.undoSendSeconds),
        photoCleaning = PhotoCleaning.decode(string(PHOTO_CLEANING)),
    )
}

private fun KeyValueEditor.put(write: WriteBehavior) {
    put(RECENT_FROM, TextCodec.Lines.encode(write.recentFrom))
    put(SIGNATURE_IN_REPLIES, write.signatureInReplies)
    put(UNDO_SEND_SECONDS, write.undoSendSeconds)
    put(PHOTO_CLEANING, write.photoCleaning.storageValue)
}

private fun KeyValueStore.actionBehavior(): ActionBehavior {
    val defaults = ActionBehavior()
    return ActionBehavior(
        startToEnd = MailAction.decodeSwipe(string(START_TO_END), defaults.startToEnd),
        endToStart = MailAction.decodeSwipe(string(END_TO_START), defaults.endToStart),
        bars = ActionBarPlace.entries.associateWith { ActionBarLayout.decode(string(barKey(it)), it) },
        barMode = ActionBarMode.fromStorageValue(string(ACTION_BAR_MODE)) ?: defaults.barMode,
        minimizedBar = flag(MINIMIZED_BAR, defaults.minimizedBar),
        notificationActions = decoded(NOTIFICATION_ACTIONS, defaults.notificationActions, ::decodeNotificationActions),
    )
}

private fun KeyValueEditor.put(actions: ActionBehavior) {
    put(START_TO_END, MailAction.encodeSwipe(actions.startToEnd))
    put(END_TO_START, MailAction.encodeSwipe(actions.endToStart))
    actions.bars.forEach { (place, layout) -> put(barKey(place), layout.encode()) }
    put(ACTION_BAR_MODE, actions.barMode.storageValue)
    put(MINIMIZED_BAR, actions.minimizedBar)
    put(NOTIFICATION_ACTIONS, encodeNotificationActions(actions.notificationActions))
}

private fun KeyValueStore.avatarBehavior(): AvatarBehavior {
    val defaults = AvatarBehavior()
    return AvatarBehavior(
        websiteIcons = flag(WEBSITE_ICONS, defaults.websiteIcons),
        // Anyone who turned on Ask DuckDuckGo before the icon source choice existed keeps DuckDuckGo.
        iconSource =
            IconSource.decode(string(ICON_SOURCE))
                ?: IconSource.DuckDuckGo.takeIf { flag(DUCKDUCKGO_ICONS, false) }
                ?: defaults.iconSource,
        bulwarkAddress = string(BULWARK_ADDRESS) ?: defaults.bulwarkAddress,
        allAccountPhotos = flag(ALL_ACCOUNT_PHOTOS, defaults.allAccountPhotos),
    )
}

private fun KeyValueEditor.put(avatars: AvatarBehavior) {
    put(WEBSITE_ICONS, avatars.websiteIcons)
    put(ICON_SOURCE, avatars.iconSource.storageValue)
    put(BULWARK_ADDRESS, avatars.bulwarkAddress)
    put(ALL_ACCOUNT_PHOTOS, avatars.allAccountPhotos)
}

private fun KeyValueStore.privacyBehavior(): PrivacyBehavior {
    val defaults = PrivacyBehavior()
    return PrivacyBehavior(
        screenSecurity = flag(SCREEN_SECURITY, defaults.screenSecurity),
        incognitoKeyboard = flag(INCOGNITO_KEYBOARD, defaults.incognitoKeyboard),
    )
}

private fun KeyValueEditor.put(privacy: PrivacyBehavior) {
    put(SCREEN_SECURITY, privacy.screenSecurity)
    put(INCOGNITO_KEYBOARD, privacy.incognitoKeyboard)
}

private fun KeyValueStore.sidebarBehavior(): SidebarBehavior {
    val defaults = SidebarBehavior()
    return SidebarBehavior(
        layout = decoded(SIDEBAR, defaults.layout, SidebarLayout::decode),
        startPage = decoded(START_PAGE, SidebarPin::fromStorageValue) ?: defaults.startPage,
    )
}

private fun KeyValueEditor.put(sidebar: SidebarBehavior) {
    put(SIDEBAR, SidebarLayout.encode(sidebar.layout))
    put(START_PAGE, sidebar.startPage.storageValue)
}

private val RemoteImages.storageValue: String
    get() = name.lowercase()

/**
 * The image setting saved under its own key, [stored], else Never when a key it had before, in
 * [earlier], held Never. Each earlier key held its default for anyone who saved any setting, Known
 * and then Always, so an earlier value is kept only when it was a choice and differs from the
 * default now, which is Never alone. Null for a value [storageValue] cannot have made.
 */
internal fun decodeRemoteImages(
    stored: String?,
    vararg earlier: String?,
): RemoteImages? =
    RemoteImages.entries.firstOrNull { it.storageValue == stored }
        ?: RemoteImages.Never.takeIf { it.storageValue in earlier }

private fun barKey(place: ActionBarPlace) = "action_bar_${place.storageValue}"

private const val START_TO_END = "swipe_start_to_end"
private const val END_TO_START = "swipe_end_to_start"
private const val MARK_READ_ON_OPEN = "mark_read_on_open"
private const val READ_LATER = "read_later"
private const val QUICK_VIEW = "quick_view_modules"
private const val READ_QUICK_VIEW = "read_quick_view_modules"
private const val PILLS = "inbox_pills"
private const val SIDEBAR = "sidebar_layout"
private const val ACTION_BAR_MODE = "action_bar_mode"
private const val MINIMIZED_BAR = "action_bar_minimized"

// Website icons moved here when they turned off by default, since the earlier key held on for
// anyone who saved any setting.
private const val WEBSITE_ICONS = "load_website_icons"
private const val ICON_SOURCE = "website_icon_source"
private const val BULWARK_ADDRESS = "bulwark_address"

// Ask DuckDuckGo, a switch for sites without an icon, before the icon source choice.
private const val DUCKDUCKGO_ICONS = "duckduckgo_icons"
private const val ALL_ACCOUNT_PHOTOS = "all_account_photos"
private const val SHOW_QUOTES = "show_quotes"
private const val UPDATE_TOASTS = "update_toasts"
private const val ZEN_READ = "read_zen"
private const val SCREEN_SECURITY = "screen_security"
private const val INCOGNITO_KEYBOARD = "incognito_keyboard"
private const val PHOTO_CLEANING = "photo_cleaning"
private const val RECENT_FROM = "recent_from"
private const val SIGNATURE_IN_REPLIES = "signature_in_replies"
private const val UNDO_SEND_SECONDS = "undo_send_seconds"
private const val INBOX = "inbox_contents"
private const val FILTERS = "filter_layout"
private const val LIST = "mail_list"
private const val REMOTE_IMAGES = "remote_images_setting"

// The keys the setting had while images loaded always, and before that, by default.
private const val ALWAYS_REMOTE_IMAGES = "load_remote_images"
private const val EARLIER_REMOTE_IMAGES = "remote_images"
private const val ALLOWED_SENDERS = "allowed_senders"
private const val NOTIFICATION_ACTIONS = "notification_actions"
private const val START_PAGE = "start_page"
