package com.otus.mail

import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.MessageSwipe
import platform.Foundation.NSUserDefaults

class IosMailBehaviorStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : MailBehaviorStore {
    override fun read(): MailBehavior {
        val defaults = MailBehavior()
        val readLater = bool(READ_LATER, defaults.readLater)
        return MailBehavior(
            startToEnd = MessageSwipe.fromStorageValue(preferences.stringForKey(START_TO_END)) ?: defaults.startToEnd,
            endToStart = MessageSwipe.fromStorageValue(preferences.stringForKey(END_TO_START)) ?: defaults.endToStart,
            markReadOnOpen = bool(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
            readLater = readLater,
            pills = preferences.stringForKey(PILLS)?.let(InboxPill::decode) ?: InboxPill.defaults(readLater),
            combinedFolders = bool(COMBINED_FOLDERS, defaults.combinedFolders),
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.setObject(behavior.startToEnd.storageValue, forKey = START_TO_END)
        preferences.setObject(behavior.endToStart.storageValue, forKey = END_TO_START)
        preferences.setBool(behavior.markReadOnOpen, forKey = MARK_READ_ON_OPEN)
        preferences.setBool(behavior.readLater, forKey = READ_LATER)
        preferences.setObject(InboxPill.encode(behavior.pills), forKey = PILLS)
        preferences.setBool(behavior.combinedFolders, forKey = COMBINED_FOLDERS)
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
    }
}
