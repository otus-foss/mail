package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.SidebarLayout
import com.otus.mail.feature.inbox.domain.decodeRecentFrom
import com.otus.mail.feature.inbox.domain.encodeRecentFrom

class AndroidMailBehaviorStore(
    context: Context,
) : MailBehaviorStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "otus_mail_behavior",
            Context.MODE_PRIVATE,
        )

    override fun read(): MailBehavior {
        val defaults = MailBehavior()
        val readLater = preferences.getBoolean(READ_LATER, defaults.readLater)
        return MailBehavior(
            startToEnd = MailAction.decodeSwipe(preferences.getString(START_TO_END, null), defaults.startToEnd),
            endToStart = MailAction.decodeSwipe(preferences.getString(END_TO_START, null), defaults.endToStart),
            markReadOnOpen = preferences.getBoolean(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
            readLater = readLater,
            pills = preferences.getString(PILLS, null)?.let(InboxPill::decode) ?: InboxPill.defaults(readLater),
            sidebar = preferences.getString(SIDEBAR, null)?.let(SidebarLayout::decode) ?: defaults.sidebar,
            actionBar =
                ActionBarPlace.entries.associateWith { place ->
                    ActionBarLayout.decode(preferences.getString(actionBarKey(place), null), place)
                },
            actionBarMode =
                ActionBarMode.fromStorageValue(preferences.getString(ACTION_BAR_MODE, null)) ?: defaults.actionBarMode,
            minimizedBar = preferences.getBoolean(MINIMIZED_BAR, defaults.minimizedBar),
            websiteIcons = preferences.getBoolean(WEBSITE_ICONS, defaults.websiteIcons),
            recentFrom = decodeRecentFrom(preferences.getString(RECENT_FROM, null)),
            signatureInReplies = preferences.getBoolean(SIGNATURE_IN_REPLIES, defaults.signatureInReplies),
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.edit {
            putString(START_TO_END, MailAction.encodeSwipe(behavior.startToEnd))
            putString(END_TO_START, MailAction.encodeSwipe(behavior.endToStart))
            putBoolean(MARK_READ_ON_OPEN, behavior.markReadOnOpen)
            putBoolean(READ_LATER, behavior.readLater)
            putString(PILLS, InboxPill.encode(behavior.pills))
            putString(SIDEBAR, SidebarLayout.encode(behavior.sidebar))
            behavior.actionBar.forEach { (place, layout) -> putString(actionBarKey(place), layout.encode()) }
            putString(ACTION_BAR_MODE, behavior.actionBarMode.storageValue)
            putBoolean(MINIMIZED_BAR, behavior.minimizedBar)
            putBoolean(WEBSITE_ICONS, behavior.websiteIcons)
            putString(RECENT_FROM, encodeRecentFrom(behavior.recentFrom))
            putBoolean(SIGNATURE_IN_REPLIES, behavior.signatureInReplies)
        }
    }

    private companion object {
        const val START_TO_END = "swipe_start_to_end"
        const val END_TO_START = "swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "mark_read_on_open"
        const val READ_LATER = "read_later"
        const val PILLS = "inbox_pills"
        const val SIDEBAR = "sidebar_layout"
        const val ACTION_BAR_MODE = "action_bar_mode"
        const val MINIMIZED_BAR = "action_bar_minimized"
        const val WEBSITE_ICONS = "website_icons"
        const val RECENT_FROM = "recent_from"
        const val SIGNATURE_IN_REPLIES = "signature_in_replies"

        fun actionBarKey(place: ActionBarPlace) = "action_bar_${place.storageValue}"
    }
}
