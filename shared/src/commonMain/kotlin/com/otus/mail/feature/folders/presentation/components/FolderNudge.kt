package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.feedback.components.ToastMessage
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.mailbox.FolderKey
import com.otus.mail.core.mailbox.MailFolder
import com.otus.mail.core.mailbox.key
import com.otus.mail.core.messages.domain.FolderKind
import com.otus.mail.core.messages.domain.MailMessage
import com.otus.mail.core.messages.presentation.ListNudge
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FolderNudgeState
import com.otus.mail.feature.folders.presentation.NUDGE_COLORS

/**
 * The folder color nudge for the folder among [messages]' rows that most needs a color, or null.
 * Showing it records the folder, so it never comes back after this launch. Picking a color says so
 * in [toasts], whose Undo takes the color away again.
 */
@Composable
fun rememberFolderNudge(
    nudge: FolderNudgeState,
    colors: FolderColorsState,
    toasts: ToastState,
    messages: List<MailMessage>,
): ListNudge? {
    val visible = remember(messages) { messages.folderCounts() }
    val folder = nudge.pick(visible)?.folder ?: return null
    LaunchedEffect(folder.key) { nudge.markShown(folder.key) }
    return ListNudge(
        folder.name,
        NUDGE_COLORS,
        onPick = { color -> colors.colorWithUndo(folder, color, toasts) },
        onDismiss = { nudge.dismiss(folder.key) },
    )
}

/** How many of [this] conversations show each of the user's own folders. */
private fun List<MailMessage>.folderCounts(): Map<FolderKey, Int> =
    flatMap { message ->
        message.labels
            .filter { it.kind == FolderKind.Folder && !it.readLater }
            .map { FolderKey(message.location.accountId, it.id) }
    }.groupingBy { it }.eachCount()

private fun FolderColorsState.colorWithUndo(
    folder: MailFolder,
    color: FolderColor,
    toasts: ToastState,
) {
    setColor(listOf(folder), color.name)
    val undo = UiAction("Undo") { setColor(listOf(folder), null) }
    toasts.show(ToastMessage("${folder.name} is now ${color.label.lowercase()}", undo))
}
