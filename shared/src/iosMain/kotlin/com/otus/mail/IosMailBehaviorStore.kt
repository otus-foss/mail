package com.otus.mail

import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.MessageSwipe
import platform.Foundation.NSUserDefaults

class IosMailBehaviorStore(
    private val preferences: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : MailBehaviorStore {
    override fun read(): MailBehavior {
        val defaults = MailBehavior()
        return MailBehavior(
            startToEnd = MessageSwipe.fromStorageValue(preferences.stringForKey(START_TO_END)) ?: defaults.startToEnd,
            endToStart = MessageSwipe.fromStorageValue(preferences.stringForKey(END_TO_START)) ?: defaults.endToStart,
            // boolForKey cannot tell a saved false from a missing value.
            markReadOnOpen =
                if (preferences.objectForKey(MARK_READ_ON_OPEN) == null) {
                    defaults.markReadOnOpen
                } else {
                    preferences.boolForKey(MARK_READ_ON_OPEN)
                },
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.setObject(behavior.startToEnd.storageValue, forKey = START_TO_END)
        preferences.setObject(behavior.endToStart.storageValue, forKey = END_TO_START)
        preferences.setBool(behavior.markReadOnOpen, forKey = MARK_READ_ON_OPEN)
    }

    private companion object {
        const val START_TO_END = "otus.mail.swipe_start_to_end"
        const val END_TO_START = "otus.mail.swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "otus.mail.mark_read_on_open"
    }
}
