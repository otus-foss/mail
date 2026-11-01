package com.otus.mail.app

import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.shared.feedback.elements.ToastAction
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MoveTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Offers to undo each move with a toast. Moves to one folder while its toast is up count up in that
 * toast, such as "3 emails deleted", and its Undo puts every one of them back. The undos run in
 * [work], which outlives the activity like the toasts, so an Undo after a rotation still puts the
 * mail back.
 */
internal class MoveToasts(
    val toasts: ToastState,
    private val work: CoroutineScope,
) {
    // The undos of the moves each folder's toast counts, keyed like the toast's group.
    private val undos = mutableMapOf<String, List<suspend () -> Unit>>()

    fun moved(
        target: MoveTarget,
        undo: suspend () -> Unit,
    ) {
        val group = target.folderId
        val counted = undos[group].takeIf { toasts.isShowing(group) }.orEmpty() + undo
        undos[group] = counted
        val action =
            ToastAction("Undo") {
                undos.remove(group)
                counted.forEach { moveBack -> work.launch { moveBack() } }
            }
        toasts.show(ToastMessage(movedText(target, counted.size), action, group))
    }
}

/** What a toast says after [count] messages moved to [target]. */
internal fun movedText(
    target: MoveTarget,
    count: Int,
): String {
    val mail = if (count == 1) "Email" else "$count emails"
    return when (target.shortcut) {
        FolderShortcut.Trash -> "$mail deleted"
        FolderShortcut.Archive -> "$mail archived"
        FolderShortcut.Spam -> "$mail marked as spam"
        FolderShortcut.Inbox, null -> if (count == 1) "Moved to ${target.name}" else "$mail moved to ${target.name}"
    }
}

/**
 * Shows the outcome of an Undo that may outlive the activity that offered it: the screen on show
 * reloads its folders and inbox, as after a push, and a failure shows as a toast.
 */
internal fun MailServices.movedBack(done: Boolean) {
    if (done) serverChanges.changed() else moveToasts.toasts.show(ToastMessage("Couldn't undo the move"))
}
