package com.otus.mail

import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.InboxContents
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.decodeRecentFrom
import com.otus.mail.feature.inbox.domain.encodeRecentFrom
import platform.Foundation.NSUserDefaults

class IosMailBehaviorStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : MailBehaviorStore {
    override fun read(): MailBehavior {
        val defaults = MailBehavior()
        val readLater = bool(READ_LATER, defaults.readLater)
        return MailBehavior(
            startToEnd = MailAction.decodeSwipe(preferences.stringForKey(START_TO_END), defaults.startToEnd),
            endToStart = MailAction.decodeSwipe(preferences.stringForKey(END_TO_START), defaults.endToStart),
            markReadOnOpen = bool(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
            readLater = readLater,
            pills = preferences.stringForKey(PILLS)?.let(InboxPill::decode) ?: InboxPill.defaults(readLater),
            sidebar = preferences.stringForKey(SIDEBAR)?.let(SidebarLayout::decode) ?: defaults.sidebar,
            actionBar =
                ActionBarPlace.entries.associateWith { place ->
                    ActionBarLayout.decode(preferences.stringForKey(actionBarKey(place)), place)
                },
            actionBarMode =
                ActionBarMode.fromStorageValue(preferences.stringForKey(ACTION_BAR_MODE)) ?: defaults.actionBarMode,
            minimizedBar = bool(MINIMIZED_BAR, defaults.minimizedBar),
            websiteIcons = bool(WEBSITE_ICONS, defaults.websiteIcons),
            recentFrom = decodeRecentFrom(preferences.stringForKey(RECENT_FROM)),
            signatureInReplies = bool(SIGNATURE_IN_REPLIES, defaults.signatureInReplies),
            undoSendSeconds = int(UNDO_SEND_SECONDS, defaults.undoSendSeconds),
            inbox = preferences.stringForKey(INBOX)?.let(InboxContents::decode) ?: defaults.inbox,
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.setObject(MailAction.encodeSwipe(behavior.startToEnd), forKey = START_TO_END)
        preferences.setObject(MailAction.encodeSwipe(behavior.endToStart), forKey = END_TO_START)
        preferences.setBool(behavior.markReadOnOpen, forKey = MARK_READ_ON_OPEN)
        preferences.setBool(behavior.readLater, forKey = READ_LATER)
        preferences.setObject(InboxPill.encode(behavior.pills), forKey = PILLS)
        preferences.setObject(SidebarLayout.encode(behavior.sidebar), forKey = SIDEBAR)
        behavior.actionBar.forEach { (place, layout) ->
            preferences.setObject(layout.encode(), forKey = actionBarKey(place))
        }
        preferences.setObject(behavior.actionBarMode.storageValue, forKey = ACTION_BAR_MODE)
        preferences.setBool(behavior.minimizedBar, forKey = MINIMIZED_BAR)
        preferences.setBool(behavior.websiteIcons, forKey = WEBSITE_ICONS)
        preferences.setObject(encodeRecentFrom(behavior.recentFrom), forKey = RECENT_FROM)
        preferences.setBool(behavior.signatureInReplies, forKey = SIGNATURE_IN_REPLIES)
        preferences.setInteger(behavior.undoSendSeconds.toLong(), forKey = UNDO_SEND_SECONDS)
        preferences.setObject(InboxContents.encode(behavior.inbox), forKey = INBOX)
    }

    // boolForKey cannot tell a saved false from a missing value.
    private fun bool(
        key: String,
        default: Boolean,
    ): Boolean = if (preferences.objectForKey(key) == null) default else preferences.boolForKey(key)

    private fun int(
        key: String,
        default: Int,
    ): Int = if (preferences.objectForKey(key) == null) default else preferences.integerForKey(key).toInt()

    private companion object {
        const val START_TO_END = "otus.mail.swipe_start_to_end"
        const val END_TO_START = "otus.mail.swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "otus.mail.mark_read_on_open"
        const val READ_LATER = "otus.mail.read_later"
        const val PILLS = "otus.mail.inbox_pills"
        const val SIDEBAR = "otus.mail.sidebar_layout"
        const val ACTION_BAR_MODE = "otus.mail.action_bar_mode"
        const val MINIMIZED_BAR = "otus.mail.action_bar_minimized"
        const val WEBSITE_ICONS = "otus.mail.website_icons"
        const val RECENT_FROM = "otus.mail.recent_from"
        const val SIGNATURE_IN_REPLIES = "otus.mail.signature_in_replies"
        const val UNDO_SEND_SECONDS = "otus.mail.undo_send_seconds"
        const val INBOX = "otus.mail.inbox_contents"

        fun actionBarKey(place: ActionBarPlace) = "otus.mail.action_bar_${place.storageValue}"
    }
}
