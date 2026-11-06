package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.hasFlag
import com.otus.mail.feature.inbox.domain.memberMailboxes
import com.otus.mail.feature.inbox.domain.withFlag
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Read and flag marks for one message or many, such as Mark all read. The change shows at once and
 * goes to the server together, with an Undo toast that sets each message back as it was. Messages
 * the server refuses go back as they were, and a toast offers to retry just those. Undo and Retry
 * run in the toasts' work, which outlives the activity, so they still reach the server after a
 * rotation, and then reload the screen on show. The save itself runs in the [edits]' scope, the
 * screen's, which a rotation can cancel; Undo then sets back every message it was changing.
 */
internal class MessageMarking(
    private val edits: MailEdits,
) {
    /**
     * Marks for messages [feed] shows, where [can] allows them: the user's own offer Undo, the
     * mark on opening does not.
     */
    fun marks(
        feed: InboxFeedState,
        can: (MailMessage, MessageFlag) -> Boolean,
    ): MessageMarks =
        MessageMarks(
            can = can,
            set = { messages, flag, on -> mark(feed, messages, flag, on) },
            opened = { mark(feed, listOf(it), MessageFlag.Read, on = true, undoable = false) },
        )

    /** Sets [flag] to [on] for [messages] that [feed] shows; [undoable] offers the Undo toast. */
    fun mark(
        feed: InboxFeedState,
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
        undoable: Boolean = true,
    ) {
        val count = changing(messages, flag, on)
        val shown = begin(messages, flag, on)
        val saving = edits.scope.async { finish(feed, shown, flag, on) }
        if (undoable && count > 0) {
            edits.toasts.marked(flag, on, count) { restore(feed, saving.outcome(messages, flag, on)) }
        }
    }

    /**
     * What the save did. A save cancelled with its screen may still have reached the server, so then
     * every message counts as changed, and Undo sends each one's earlier mark back, which is harmless
     * where the server never applied the change.
     */
    private suspend fun Deferred<MarkOutcome>.outcome(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): MarkOutcome {
        join()
        return if (isCancelled) MarkOutcome(messages, messages.map { it.id }.toSet(), flag, on) else await()
    }

    /** Shows the change at once; each message goes back to its own mark if refused. */
    private fun begin(
        messages: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ): List<Pair<MailMessage, MailEdit>> =
        messages.map { message ->
            message to edits.show(markEdit(message, flag, on))
        }

    /** Saves [shown] on the server, rolls back the refused ones, and offers to retry them. */
    private suspend fun finish(
        feed: InboxFeedState,
        shown: List<Pair<MailMessage, MailEdit>>,
        flag: MessageFlag,
        on: Boolean,
    ): MarkOutcome {
        val messages = shown.map { it.first }
        val marked = edits.writer.mark(messages, flag, on)
        edits.settle(shown.associate { (message, edit) -> edit to (message.id in marked) })
        val outcome = MarkOutcome(messages, marked, flag, on)
        val failed = outcome.failed
        if (failed.isNotEmpty()) {
            feed.refresh()
            edits.toasts.failed(notMarkedText(flag, on, failed.size, messages.size)) { retry(feed, failed, flag, on) }
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
        val back = finish(feed, begin(changed, outcome.flag, !outcome.on), outcome.flag, !outcome.on)
        if (back.marked.isNotEmpty()) edits.reloadAfterUndo()
    }

    /** Sends [failed] again; the ones it changes get their own Undo toast. */
    private suspend fun retry(
        feed: InboxFeedState,
        failed: List<MailMessage>,
        flag: MessageFlag,
        on: Boolean,
    ) {
        val outcome = finish(feed, begin(failed, flag, on), flag, on)
        val count = changing(outcome.messages.filter { it.id in outcome.marked }, flag, on)
        if (count == 0) return
        edits.toasts.marked(flag, on, count) { restore(feed, outcome) }
        edits.reloadAfterUndo()
    }
}

/**
 * [flag] set to [on] on [message] and every email of its conversation, in lists and Read views.
 * Folder unread counts follow the read mark only.
 */
internal fun markEdit(
    message: MailMessage,
    flag: MessageFlag,
    on: Boolean,
): MailEdit {
    val before = message.hasFlag(flag)
    return MailEdit(
        message.id,
        message.memberMailboxes.keys,
        row = { it.withFlag(flag, on) },
        undo = { it.withFlag(flag, before) },
        page = { it.withFlag(flag, on) },
        counts = flag == MessageFlag.Read,
    )
}
