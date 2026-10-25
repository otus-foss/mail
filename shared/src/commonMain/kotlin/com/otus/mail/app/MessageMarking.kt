package com.otus.mail.app

import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Read and flag marks for one message or many, such as Mark all read. The change shows at once and
 * goes to the server together; messages the server refuses go back as they were, with a notice.
 */
internal class MessageMarking(
    private val services: MailServices,
    private val folders: FoldersState,
    private val scope: CoroutineScope,
) {
    /** Sets [flag] to [on] for [messages] that [feed] shows. */
    fun mark(
        feed: InboxFeedState,
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val changes =
            messages.associate { message ->
                val before = message.hasFlag(flag)
                message.id to feed.change(message.id, { it.withFlag(flag, on) }, undo = { it.withFlag(flag, before) })
            }
        scope.launch {
            val marked = services.marker.mark(messages, flag, on)
            changes.forEach { (id, change) -> if (id in marked) change.confirm() else change.rollBack() }
            // Folder unread counts follow the read mark; one refresh covers every message.
            if (flag == MessageFlag.Read) folders.refresh()
            val kept = changes.keys.count { it !in marked }
            val what = if (changes.size == 1) "the message" else countText(kept)
            if (kept > 0) {
                feed.refresh()
                feed.report("Couldn't finish changing $what. Try again.")
            }
        }
    }
}
