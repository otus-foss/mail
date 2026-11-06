package com.otus.mail.core.settings.data

import com.otus.avatar.IconSource
import com.otus.mail.core.messages.domain.ActionBarLayout
import com.otus.mail.core.messages.domain.ActionBarMode
import com.otus.mail.core.messages.domain.ActionBarPlace
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.settings.domain.FilterLayout
import com.otus.mail.core.settings.domain.InboxContents
import com.otus.mail.core.settings.domain.InboxPill
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.domain.MailBehaviorStore
import com.otus.mail.core.settings.domain.MailListLayout
import com.otus.mail.core.settings.domain.PhotoCleaning
import com.otus.mail.core.settings.domain.QuickViewModule
import com.otus.mail.core.settings.domain.ReadQuickModule
import com.otus.mail.core.settings.domain.SidebarLayout
import com.otus.mail.core.settings.domain.SidebarPin
import com.otus.mail.core.settings.domain.decodeAddresses
import com.otus.mail.core.settings.domain.decodeNotificationActions
import com.otus.mail.core.settings.domain.decodeRemoteImages
import com.otus.mail.core.settings.domain.encodeAddresses
import com.otus.mail.core.settings.domain.encodeNotificationActions
import com.otus.mail.core.settings.domain.storageValue
import com.otus.storage.KeyValueStore
import com.otus.storage.decoded
import com.otus.storage.flag
import com.otus.storage.int

/** Keeps [MailBehavior] in [values], one key per setting. */
class KeyValueMailBehaviorStore(
    private val values: KeyValueStore,
) : MailBehaviorStore {
    override fun read(): MailBehavior =
        with(values) {
            val defaults = MailBehavior()
            val readLater = flag(READ_LATER, defaults.readLater)
            MailBehavior(
                startToEnd = MailAction.decodeSwipe(string(START_TO_END), defaults.startToEnd),
                endToStart = MailAction.decodeSwipe(string(END_TO_START), defaults.endToStart),
                markReadOnOpen = flag(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
                readLater = readLater,
                pills = decoded(PILLS, InboxPill::decode) ?: InboxPill.defaults(readLater),
                sidebar = decoded(SIDEBAR, defaults.sidebar, SidebarLayout::decode),
                actionBar = ActionBarPlace.entries.associateWith { ActionBarLayout.decode(string(barKey(it)), it) },
                actionBarMode = ActionBarMode.fromStorageValue(string(ACTION_BAR_MODE)) ?: defaults.actionBarMode,
                minimizedBar = flag(MINIMIZED_BAR, defaults.minimizedBar),
                websiteIcons = flag(WEBSITE_ICONS, defaults.websiteIcons),
                iconSource = savedIconSource() ?: defaults.iconSource,
                bulwarkAddress = string(BULWARK_ADDRESS) ?: defaults.bulwarkAddress,
                allAccountPhotos = flag(ALL_ACCOUNT_PHOTOS, defaults.allAccountPhotos),
                showQuotes = flag(SHOW_QUOTES, defaults.showQuotes),
                zenRead = flag(ZEN_READ, defaults.zenRead),
                screenSecurity = flag(SCREEN_SECURITY, defaults.screenSecurity),
                incognitoKeyboard = flag(INCOGNITO_KEYBOARD, defaults.incognitoKeyboard),
                photoCleaning = PhotoCleaning.decode(string(PHOTO_CLEANING)),
                recentFrom = decodeAddresses(string(RECENT_FROM)),
                signatureInReplies = flag(SIGNATURE_IN_REPLIES, defaults.signatureInReplies),
                undoSendSeconds = int(UNDO_SEND_SECONDS, defaults.undoSendSeconds),
                inbox = decoded(INBOX, defaults.inbox, InboxContents::decode),
                filters = decoded(FILTERS, defaults.filters, FilterLayout::decode),
                quickView = decoded(QUICK_VIEW, defaults.quickView, QuickViewModule::decode),
                readQuickView = decoded(READ_QUICK_VIEW, defaults.readQuickView, ReadQuickModule::decode),
                list = decoded(LIST, defaults.list, MailListLayout::decode),
                remoteImages = savedRemoteImages() ?: defaults.remoteImages,
                allowedSenders = decodeAddresses(string(ALLOWED_SENDERS)),
                notificationActions =
                    decoded(NOTIFICATION_ACTIONS, defaults.notificationActions, ::decodeNotificationActions),
                startPage = decoded(START_PAGE, SidebarPin::fromStorageValue) ?: defaults.startPage,
            )
        }

    override fun write(behavior: MailBehavior) {
        values.edit {
            put(START_TO_END, MailAction.encodeSwipe(behavior.startToEnd))
            put(END_TO_START, MailAction.encodeSwipe(behavior.endToStart))
            put(MARK_READ_ON_OPEN, behavior.markReadOnOpen)
            put(READ_LATER, behavior.readLater)
            put(PILLS, InboxPill.encode(behavior.pills))
            put(SIDEBAR, SidebarLayout.encode(behavior.sidebar))
            behavior.actionBar.forEach { (place, layout) -> put(barKey(place), layout.encode()) }
            put(ACTION_BAR_MODE, behavior.actionBarMode.storageValue)
            put(MINIMIZED_BAR, behavior.minimizedBar)
            put(WEBSITE_ICONS, behavior.websiteIcons)
            put(ICON_SOURCE, behavior.iconSource.storageValue)
            put(BULWARK_ADDRESS, behavior.bulwarkAddress)
            put(ALL_ACCOUNT_PHOTOS, behavior.allAccountPhotos)
            put(SHOW_QUOTES, behavior.showQuotes)
            put(ZEN_READ, behavior.zenRead)
            put(SCREEN_SECURITY, behavior.screenSecurity)
            put(INCOGNITO_KEYBOARD, behavior.incognitoKeyboard)
            put(PHOTO_CLEANING, behavior.photoCleaning.storageValue)
            put(RECENT_FROM, encodeAddresses(behavior.recentFrom))
            put(SIGNATURE_IN_REPLIES, behavior.signatureInReplies)
            put(UNDO_SEND_SECONDS, behavior.undoSendSeconds)
            put(INBOX, InboxContents.encode(behavior.inbox))
            put(FILTERS, FilterLayout.encode(behavior.filters))
            put(QUICK_VIEW, QuickViewModule.encode(behavior.quickView))
            put(READ_QUICK_VIEW, ReadQuickModule.encode(behavior.readQuickView))
            put(LIST, MailListLayout.encode(behavior.list))
            put(REMOTE_IMAGES, behavior.remoteImages.storageValue)
            put(ALLOWED_SENDERS, encodeAddresses(behavior.allowedSenders))
            put(NOTIFICATION_ACTIONS, encodeNotificationActions(behavior.notificationActions))
            put(START_PAGE, behavior.startPage.storageValue)
        }
    }

    /** The saved choice, or DuckDuckGo for anyone who turned on Ask DuckDuckGo before the choice existed. */
    private fun savedIconSource() =
        IconSource.decode(values.string(ICON_SOURCE))
            ?: IconSource.DuckDuckGo.takeIf { values.flag(DUCKDUCKGO_ICONS, false) }

    private fun savedRemoteImages() =
        decodeRemoteImages(
            values.string(REMOTE_IMAGES),
            values.string(ALWAYS_REMOTE_IMAGES),
            values.string(EARLIER_REMOTE_IMAGES),
        )

    private companion object {
        const val START_TO_END = "swipe_start_to_end"
        const val END_TO_START = "swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "mark_read_on_open"
        const val READ_LATER = "read_later"
        const val QUICK_VIEW = "quick_view_modules"
        const val READ_QUICK_VIEW = "read_quick_view_modules"
        const val PILLS = "inbox_pills"
        const val SIDEBAR = "sidebar_layout"
        const val ACTION_BAR_MODE = "action_bar_mode"
        const val MINIMIZED_BAR = "action_bar_minimized"

        // Website icons moved here when they turned off by default, since the earlier key held on for
        // anyone who saved any setting.
        const val WEBSITE_ICONS = "load_website_icons"
        const val ICON_SOURCE = "website_icon_source"
        const val BULWARK_ADDRESS = "bulwark_address"

        // Ask DuckDuckGo, a switch for sites without an icon, before the icon source choice.
        const val DUCKDUCKGO_ICONS = "duckduckgo_icons"
        const val ALL_ACCOUNT_PHOTOS = "all_account_photos"
        const val SHOW_QUOTES = "show_quotes"
        const val ZEN_READ = "read_zen"
        const val SCREEN_SECURITY = "screen_security"
        const val INCOGNITO_KEYBOARD = "incognito_keyboard"
        const val PHOTO_CLEANING = "photo_cleaning"
        const val RECENT_FROM = "recent_from"
        const val SIGNATURE_IN_REPLIES = "signature_in_replies"
        const val UNDO_SEND_SECONDS = "undo_send_seconds"
        const val INBOX = "inbox_contents"
        const val FILTERS = "filter_layout"
        const val LIST = "mail_list"
        const val REMOTE_IMAGES = "remote_images_setting"

        // The keys the setting had while images loaded always, and before that, by default.
        const val ALWAYS_REMOTE_IMAGES = "load_remote_images"
        const val EARLIER_REMOTE_IMAGES = "remote_images"
        const val ALLOWED_SENDERS = "allowed_senders"
        const val NOTIFICATION_ACTIONS = "notification_actions"
        const val START_PAGE = "start_page"

        fun barKey(place: ActionBarPlace) = "action_bar_${place.storageValue}"
    }
}
