package com.otus.mail.app

import com.otus.mail.feature.folders.domain.FolderRights
import com.otus.mail.feature.folders.presentation.FoldersState
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.withFlag
import com.otus.mail.feature.inbox.presentation.InboxFeedState
import com.otus.mail.feature.inbox.presentation.LocalChange
import com.otus.mail.feature.inbox.presentation.MessageMarks
import kotlinx.coroutines.async

/**
 * Read and flag marks for one message or many, such as Mark all read. The change shows at once and
 * goes to the server together, with an Undo toast that sets each message back as it was. Messages
 * the server refuses go back as they were, and a toast offers to retry just those. The save, Undo,
 * and Retry run in the toasts' work, which outlives the activity, so a rotation cannot cancel a save
 * that Undo waits for; [MailServices.serverChanges] then reloads the screen on show.
 */
internal class MessageMarking(
    private val services: MailServices,
    private val folders: FoldersState,
    private val toasts: MoveToasts,
) {
    /** Marks for messages [feed] shows: the user's own offer Undo, the mark on opening does not. */
    fun marks(feed: InboxFeedState): MessageMarks =
        MessageMarks(
            can = ::canMark,
            set = { messages, flag, on -> mark(feed, messages, flag, on) },
            opened = { mark(feed, listOf(it), MessageFlag.Read, on = true, undoable = false) },
        )

    /** Supplied messages have no server id, so only loaded mail can change its marks. */
    private fun canMark(
        message: MailMessage,
        flag: MessageFlag,
    ): Boolean = message.emailId.isNotEmpty() && folders.rightsOf(message).allow(flag)

    /** Sets [flag] to [on] for [messages] that [feed] shows; [undoable] offers the Undo toast. */
    fun mark(
        feed: InboxFeedState,
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
        undoable: Boolean = true,
    ) {
        val count = changing(messages, flag, on)
        val changes = begin(feed, messages, flag, on)
        // In the toasts' scope, so a recreated activity cannot cancel the save that Undo waits for.
        val saving = toasts.work.async { finish(feed, changes, flag, on) }
        if (undoable && count > 0) toasts.marked(flag, on, count) { restore(feed, saving.await()) }
    }

    /** Shows the change in [feed] at once; each message goes back to its own mark if refused. */
    private fun begin(
        feed: InboxFeedState,
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): List<Pair<MailMessage, LocalChange>> =
        messages.map { message ->
            val before = message.hasFlag(flag)
            message to feed.change(message.id, { it.withFlag(flag, on) }, undo = { it.withFlag(flag, before) })
        }

    /** Saves [changes] on the server, rolls back the refused ones, and offers to retry them. */
    private suspend fun finish(
        feed: InboxFeedState,
        changes: List<Pair<MailMessage, LocalChange>>,
        flag: MessageFlag,
        on: Boolean,
    ): MarkOutcome {
        val messages = changes.map { it.first }
        val marked = services.marker.mark(messages, flag, on)
        changes.forEach { (message, change) -> if (message.id in marked) change.confirm() else change.rollBack() }
        // Folder unread counts follow the read mark; one refresh covers every message.
        if (flag == MessageFlag.Read) folders.refresh()
        val outcome = MarkOutcome(messages, marked, flag, on)
        val failed = outcome.failed
        if (failed.isNotEmpty()) {
            feed.refresh()
            toasts.failed(notMarkedText(flag, on, failed.size, messages.size)) { retry(feed, failed, flag, on) }
        }
        return outcome
    }

    /** Sets back what [outcome] changed, leaving messages that already had the mark as they are. */
    private suspend fun restore(
        feed: InboxFeedState,
        outcome: MarkOutcome,
    ) {
        val changed = outcome.changed
        if (changed.isEmpty()) return
        val back = finish(feed, begin(feed, changed, outcome.flag, !outcome.on), outcome.flag, !outcome.on)
        if (back.marked.isNotEmpty()) services.serverChanges.changed()
    }

    /** Sends [failed] again; the ones it changes get their own Undo toast. */
    private suspend fun retry(
        feed: InboxFeedState,
        failed: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val outcome = finish(feed, begin(feed, failed, flag, on), flag, on)
        val count = changing(outcome.messages.filter { it.id in outcome.marked }, flag, on)
        if (count == 0) return
        toasts.marked(flag, on, count) { restore(feed, outcome) }
        services.serverChanges.changed()
    }
}

private fun FolderRights.allow(flag: MessageFlag): Boolean =
    when (flag) {
        MessageFlag.Read -> maySetSeen
        MessageFlag.Flagged -> maySetKeywords
    }
