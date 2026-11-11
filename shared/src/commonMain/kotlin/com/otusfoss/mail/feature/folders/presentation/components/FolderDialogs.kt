package com.otusfoss.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otusfoss.core.designsystem.shared.overlays.components.ConfirmDialog
import com.otusfoss.core.designsystem.shared.overlays.components.InputSheet
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.domain.folderNameProblem
import com.otusfoss.mail.feature.folders.presentation.FolderDialog
import com.otusfoss.mail.feature.folders.presentation.message

/** Shows [dialog]. A folder that disappeared after a reload closes it. */
@Composable
fun FolderDialogs(
    dialog: FolderDialog,
    account: AccountFolders?,
    actions: FolderActions,
    onDialog: (FolderDialog?) -> Unit,
) {
    val close = { onDialog(null) }
    val folder = account?.let { folderOf(dialog, it) }
    when {
        account == null -> LaunchedEffect(dialog) { close() }
        dialog is FolderDialog.Create -> NewFolderSheet(account, dialog.parentId, actions, close)
        folder == null -> LaunchedEffect(dialog) { close() }
        else -> FolderChangeDialog(dialog, folder, account, actions, onDialog)
    }
}

@Composable
private fun FolderChangeDialog(
    dialog: FolderDialog,
    folder: MailFolder,
    account: AccountFolders,
    actions: FolderActions,
    onDialog: (FolderDialog?) -> Unit,
) {
    val close = { onDialog(null) }
    // FolderDialogs handles creation before looking up an existing folder.
    when (dialog) {
        is FolderDialog.Rename -> RenameFolder(folder, account, actions, close)
        is FolderDialog.Delete -> DeleteFolder(folder, actions, close)
        is FolderDialog.Move -> FolderParentSheet(folder, account, { actions.onMove(folder, it) }, close)
        is FolderDialog.Color -> ChangeFolderColor(dialog, folder, actions, close)
        is FolderDialog.Create -> Unit
    }
}

@Composable
private fun RenameFolder(
    folder: MailFolder,
    account: AccountFolders,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    InputSheet(
        title = "Rename ${folder.name}",
        label = "Name",
        confirmLabel = "Rename",
        onConfirm = { actions.onRename(folder, it) },
        onDismiss = onDismiss,
        initial = folder.name,
        problem = { folderNameProblem(it, folder.parentId, account, renaming = folder)?.message() },
    )
}

@Composable
private fun DeleteFolder(
    folder: MailFolder,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = "Delete ${folder.name}?",
        message = "This deletes the folder and any rules that sort mail into it.",
        confirmLabel = "Delete",
        onConfirm = { actions.onDelete(folder) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChangeFolderColor(
    dialog: FolderDialog.Color,
    folder: MailFolder,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    val color = actions.color(folder)
    if (color == null) {
        LaunchedEffect(dialog) { onDismiss() }
    } else {
        FolderColorSheet(color.current, color.onPick, onDismiss = onDismiss)
    }
}

private fun folderOf(
    dialog: FolderDialog,
    account: AccountFolders,
): MailFolder? =
    when (dialog) {
        is FolderDialog.Rename -> account.folder(dialog.folderId)
        is FolderDialog.Delete -> account.folder(dialog.folderId)
        is FolderDialog.Color -> account.folder(dialog.folderId)
        is FolderDialog.Move -> account.folder(dialog.folderId)
        is FolderDialog.Create -> null
    }
