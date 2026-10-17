package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.InputSheet
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.message

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
        dialog is FolderDialog.Create -> CreateFolderSheet(account, dialog.parentId, actions, close)
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
    when (dialog) {
        is FolderDialog.Rename -> {
            InputSheet(
                title = "Rename ${folder.name}",
                label = "Name",
                confirmLabel = "Rename",
                onConfirm = { actions.onRename(folder, it) },
                onDismiss = close,
                initial = folder.name,
                problem = { folderNameProblem(it, folder.parentId, account, renaming = folder)?.message() },
            )
        }

        is FolderDialog.Delete -> {
            ConfirmDialog(
                title = "Delete ${folder.name}?",
                message = "This deletes the folder and any rules that sort mail into it.",
                confirmLabel = "Delete",
                onConfirm = { actions.onDelete(folder) },
                onDismiss = close,
            )
        }

        is FolderDialog.Move -> {
            FolderParentSheet(folder, account, onPick = { actions.onMove(folder, it) }, onDismiss = close)
        }

        is FolderDialog.Color -> {
            val color = actions.color(folder)
            if (color == null) {
                LaunchedEffect(dialog) { close() }
            } else {
                FolderColorSheet(color.current, color.onPick, onDismiss = close)
            }
        }

        // Creating needs no existing folder; FolderDialogs shows it before looking one up.
        is FolderDialog.Create -> {}
    }
}

@Composable
private fun CreateFolderSheet(
    account: AccountFolders,
    parentId: String?,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    val parent = parentId?.let { account.folder(it) }
    InputSheet(
        title = parent?.let { "New folder in ${it.name}" } ?: "New folder",
        label = "Name",
        confirmLabel = "Create",
        onConfirm = { actions.onCreate(account.accountId, it, parentId) },
        onDismiss = onDismiss,
        problem = { folderNameProblem(it, parentId, account)?.message() },
    )
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
