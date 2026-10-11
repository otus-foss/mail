package com.otus.mail

import android.content.Context
import androidx.core.content.edit
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.domain.MailBehaviorStore
import com.otus.mail.feature.inbox.domain.MessageSwipe

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
        return MailBehavior(
            startToEnd =
                MessageSwipe.fromStorageValue(
                    preferences.getString(START_TO_END, null),
                ) ?: defaults.startToEnd,
            endToStart =
                MessageSwipe.fromStorageValue(
                    preferences.getString(END_TO_START, null),
                ) ?: defaults.endToStart,
            markReadOnOpen = preferences.getBoolean(MARK_READ_ON_OPEN, defaults.markReadOnOpen),
            readLater = preferences.getBoolean(READ_LATER, defaults.readLater),
        )
    }

    override fun write(behavior: MailBehavior) {
        preferences.edit {
            putString(START_TO_END, behavior.startToEnd.storageValue)
            putString(END_TO_START, behavior.endToStart.storageValue)
            putBoolean(MARK_READ_ON_OPEN, behavior.markReadOnOpen)
            putBoolean(READ_LATER, behavior.readLater)
        }
    }

    private companion object {
        const val START_TO_END = "swipe_start_to_end"
        const val END_TO_START = "swipe_end_to_start"
        const val MARK_READ_ON_OPEN = "mark_read_on_open"
        const val READ_LATER = "read_later"
    }
}
