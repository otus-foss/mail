package com.otus.mail.app

import com.otus.designsystem.components.ToastMessage
import com.otus.designsystem.components.ToastState
import com.otus.designsystem.elements.ToastAction
import com.otus.mail.feature.inbox.presentation.FolderShortcut
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * Offers to undo each move with a toast. Moves to one folder while its toast is up count up in that
 * toast, such as "3 emails deleted", and its Undo puts every one of them back.
 */
internal class MoveToasts(
    val toasts: ToastState = ToastState(),
) {
    // The undos of the moves each folder's toast counts, keyed like the toast's group.
    private val undos = mutableMapOf<String, List<() -> Unit>>()

    fun moved(
        target: MoveTarget,
        undo: () -> Unit,
    ) {
        val group = target.folderId
        val counted = undos[group].takeIf { toasts.isShowing(group) }.orEmpty() + undo
        undos[group] = counted
        val action =
            ToastAction("Undo") {
                undos.remove(group)
                counted.forEach { it() }
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
