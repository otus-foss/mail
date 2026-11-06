package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.MessageFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Offers to undo each move and mark with a toast, and to retry a change the server refused. Moves to
 * one folder while its toast is up count up in that toast, such as "3 emails deleted", and its Undo
 * puts every one of them back; marks of the same kind count up the same way. The undos and retries
 * run in [work], which outlives the activity like the toasts, so an Undo after a rotation still puts
 * the mail back.
 */
class MoveToasts(
    val toasts: ToastState,
    private val work: CoroutineScope,
) {
    // The undos of the changes each toast counts, keyed like the toast's group.
    private val undos = mutableMapOf<Any, List<CountedUndo>>()

    fun moved(
        target: MoveTarget,
        undo: suspend () -> Unit,
    ) = offer(target.folderId, count = 1, undo) { movedText(target, it) }

    /** Offers to undo setting [flag] to [on] for [count] messages. */
    fun marked(
        flag: MessageFlag,
        on: Boolean,
        count: Int,
        undo: suspend () -> Unit,
    ) = offer(MarkGroup(flag, on), count, undo) { markedText(flag, on, it) }

    /**
     * Says [text] about a change the server refused, with Retry. The toast has no timer, so it waits
     * until the user retries or swipes it away.
     */
    fun failed(
        text: String,
        retry: suspend () -> Unit,
    ) {
        toasts.show(ToastMessage(text, UiAction("Retry") { work.launch { retry() } }, sticky = true))
    }

    fun failedUndo() {
        toasts.show(ToastMessage("Couldn't undo the move"))
    }

    private fun offer(
        group: Any,
        count: Int,
        undo: suspend () -> Unit,
        text: (Int) -> String,
    ) {
        val counted = undos[group].takeIf { toasts.isShowing(group) }.orEmpty() + CountedUndo(count, undo)
        undos[group] = counted
        val action =
            UiAction("Undo") {
                undos.remove(group)
                counted.forEach { change -> work.launch { change.undo() } }
            }
        toasts.show(ToastMessage(text(counted.sumOf { it.count }), action, group))
    }
}

/** One change a toast counts: how many messages it changed, and how to set them back. */
private class CountedUndo(
    val count: Int,
    val undo: suspend () -> Unit,
)

/** The toast group of marks that set [flag] to [on]. */
private data class MarkGroup(
    val flag: MessageFlag,
    val on: Boolean,
)

/** What a toast says after [count] messages moved to [target]. */
internal fun movedText(
    target: MoveTarget,
    count: Int,
): String {
    val mail = if (count == 1) "Email" else "$count emails"
    return when (target.shortcut) {
        MailboxRole.Trash -> "$mail deleted"
        MailboxRole.Archive -> "$mail archived"
        MailboxRole.Junk -> "$mail marked as spam"
        else -> if (count == 1) "Moved to ${target.name}" else "$mail moved to ${target.name}"
    }
}
