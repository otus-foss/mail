package com.otusfoss.mail.core.settings.data

import com.otusfoss.core.avatar.IconSource
import com.otusfoss.core.storage.KeyValueEditor
import com.otusfoss.core.storage.KeyValueStore
import com.otusfoss.core.storage.decoded
import com.otusfoss.core.storage.flag
import com.otusfoss.core.storage.int
import com.otusfoss.mail.core.messages.domain.ActionBarLayout
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.messages.domain.ActionBarPlace
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.RemoteImages
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.AvatarBehavior
import com.otusfoss.mail.core.settings.domain.FilterLayout
import com.otusfoss.mail.core.settings.domain.InboxContents
import com.otusfoss.mail.core.settings.domain.InboxPill
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailBehaviorStore
import com.otusfoss.mail.core.settings.domain.MailListLayout
import com.otusfoss.mail.core.settings.domain.PhotoCleaning
import com.otusfoss.mail.core.settings.domain.PrivacyBehavior
import com.otusfoss.mail.core.settings.domain.QuickViewModule
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.domain.ReadQuickModule
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.SidebarLayout
import com.otusfoss.mail.core.settings.domain.SidebarPin
import com.otusfoss.mail.core.settings.domain.WriteBehavior
import com.otusfoss.mail.core.settings.domain.decodeNotificationActions
import com.otusfoss.mail.core.settings.domain.encodeNotificationActions
import com.otusfoss.mail.core.storage.TextCodec

internal fun KeyValueStore.actionBehavior(): ActionBehavior {
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

internal fun KeyValueEditor.put(actions: ActionBehavior) {
    put(START_TO_END, MailAction.encodeSwipe(actions.startToEnd))
    put(END_TO_START, MailAction.encodeSwipe(actions.endToStart))
    actions.bars.forEach { (place, layout) -> put(barKey(place), layout.encode()) }
    put(ACTION_BAR_MODE, actions.barMode.storageValue)
    put(MINIMIZED_BAR, actions.minimizedBar)
    put(NOTIFICATION_ACTIONS, encodeNotificationActions(actions.notificationActions))
}

internal fun KeyValueStore.avatarBehavior(): AvatarBehavior {
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

internal fun KeyValueEditor.put(avatars: AvatarBehavior) {
    put(WEBSITE_ICONS, avatars.websiteIcons)
    put(ICON_SOURCE, avatars.iconSource.storageValue)
    put(BULWARK_ADDRESS, avatars.bulwarkAddress)
    put(ALL_ACCOUNT_PHOTOS, avatars.allAccountPhotos)
}

internal fun KeyValueStore.privacyBehavior(): PrivacyBehavior {
    val defaults = PrivacyBehavior()
    return PrivacyBehavior(
        screenSecurity = flag(SCREEN_SECURITY, defaults.screenSecurity),
        incognitoKeyboard = flag(INCOGNITO_KEYBOARD, defaults.incognitoKeyboard),
    )
}

internal fun KeyValueEditor.put(privacy: PrivacyBehavior) {
    put(SCREEN_SECURITY, privacy.screenSecurity)
    put(INCOGNITO_KEYBOARD, privacy.incognitoKeyboard)
}

internal fun KeyValueStore.sidebarBehavior(): SidebarBehavior {
    val defaults = SidebarBehavior()
    return SidebarBehavior(
        layout = decoded(SIDEBAR, defaults.layout, SidebarLayout::decode),
        startPage = decoded(START_PAGE, SidebarPin::fromStorageValue) ?: defaults.startPage,
    )
}

internal fun KeyValueEditor.put(sidebar: SidebarBehavior) {
    put(SIDEBAR, SidebarLayout.encode(sidebar.layout))
    put(START_PAGE, sidebar.startPage.storageValue)
}

private fun barKey(place: ActionBarPlace) = "action_bar_${place.storageValue}"

private const val START_TO_END = "swipe_start_to_end"
private const val END_TO_START = "swipe_end_to_start"
private const val ACTION_BAR_MODE = "action_bar_mode"
private const val MINIMIZED_BAR = "action_bar_minimized"
private const val NOTIFICATION_ACTIONS = "notification_actions"
private const val ICON_SOURCE = "website_icon_source"
private const val BULWARK_ADDRESS = "bulwark_address"
private const val ALL_ACCOUNT_PHOTOS = "all_account_photos"
private const val SCREEN_SECURITY = "screen_security"
private const val INCOGNITO_KEYBOARD = "incognito_keyboard"
private const val SIDEBAR = "sidebar_layout"
private const val START_PAGE = "start_page"

// Website icons moved here when they turned off by default, since the earlier key held on for
// anyone who saved any setting.
private const val WEBSITE_ICONS = "load_website_icons"

// Ask DuckDuckGo, a switch for sites without an icon, before the icon source choice.
private const val DUCKDUCKGO_ICONS = "duckduckgo_icons"
