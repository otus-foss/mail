package com.otus.mail

import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
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
            combinedFolders = bool(COMBINED_FOLDERS, defaults.combinedFolders),
            actionBar =
                ActionBarPlace.entries.associateWith { place ->
                    ActionBarLayout.decode(preferences.stringForKey(actionBarKey(place)), place)
                },
            actionBarMode =
                ActionBarMode.fromStorageValue(preferences.stringForKey(ACTION_BAR_MODE)) ?: defaults.actionBarMode,
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.setObject(MailAction.encodeSwipe(behavior.startToEnd), forKey = START_TO_END)
        preferences.setObject(MailAction.encodeSwipe(behavior.endToStart), forKey = END_TO_START)
        preferences.setBool(behavior.markReadOnOpen, forKey = MARK_READ_ON_OPEN)
        preferences.setBool(behavior.readLater, forKey = READ_LATER)
        preferences.setObject(InboxPill.encode(behavior.pills), forKey = PILLS)
        preferences.setBool(behavior.combinedFolders, forKey = COMBINED_FOLDERS)
        behavior.actionBar.forEach { (place, layout) ->
            preferences.setObject(layout.encode(), forKey = actionBarKey(place))
        }
        preferences.setObject(behavior.actionBarMode.storageValue, forKey = ACTION_BAR_MODE)
    }

    // boolForKey cannot tell a saved false from a missing value.
    private fun bool(
        key: String,
        default: Boolean,
    ): Boolean = if (preferences.objectForKey(key) == null) default else preferences.boolForKey(key)

    private companion object {
        const val START_TO_END = "otus.mail.swipe_start_to_end"
        const val END_TO_START = "otus.mail.swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "otus.mail.mark_read_on_open"
        const val READ_LATER = "otus.mail.read_later"
        const val PILLS = "otus.mail.inbox_pills"
        const val COMBINED_FOLDERS = "otus.mail.combined_folders"
        const val ACTION_BAR_MODE = "otus.mail.action_bar_mode"

        fun actionBarKey(place: ActionBarPlace) = "otus.mail.action_bar_${place.storageValue}"
    }
}
