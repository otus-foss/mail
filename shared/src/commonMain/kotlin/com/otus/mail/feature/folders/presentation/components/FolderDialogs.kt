package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.designsystem.components.ActionDialog
import com.otus.designsystem.components.ChoiceDialog
import com.otus.designsystem.components.ConfirmDialog
import com.otus.designsystem.components.DialogAction
import com.otus.designsystem.components.InputDialog
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.domain.canCreateIn
import com.otus.mail.feature.folders.domain.folderNameProblem
import com.otus.mail.feature.folders.domain.folderTree
import com.otus.mail.feature.folders.domain.moveTargets
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
        dialog is FolderDialog.Create -> CreateFolderDialog(account, dialog.parentId, actions, close)
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
        is FolderDialog.Actions -> {
            FolderActionsDialog(folder, account, actions, onDialog)
        }

        is FolderDialog.Rename -> {
            InputDialog(
                title = "Rename ${folder.name}",
                label = "Name",
                confirmLabel = "Rename",
                onConfirm = { actions.onRename(folder, it) },
                onDismiss = close,
                initial = folder.name,
                problem = { folderNameProblem(it, folder.parentId, account, renaming = folder)?.message() },
            )
        }

        is FolderDialog.Move -> {
            val paths = account.folderTree().associate { it.folder.id to it.path }
            ChoiceDialog(
                title = "Move ${folder.name}",
                options = moveTargets(account, folder),
                selected = folder.parentId?.let { account.folder(it) },
                label = { target -> target?.let { paths[it.id] } ?: "Top level" },
                onSelect = { target -> if (target?.id != folder.parentId) actions.onMove(folder, target?.id) },
                onDismiss = close,
            )
        }

        is FolderDialog.Delete -> {
            ConfirmDialog(
                title = "Delete ${folder.name}?",
                message = "Only empty folders can be deleted. Rules for it will use the inbox.",
                confirmLabel = "Delete",
                onConfirm = { actions.onDelete(folder) },
                onDismiss = close,
            )
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
private fun CreateFolderDialog(
    account: AccountFolders,
    parentId: String?,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    val parent = parentId?.let { account.folder(it) }
    InputDialog(
        title = parent?.let { "New folder in ${it.name}" } ?: "New folder",
        label = "Name",
        confirmLabel = "Create",
        onConfirm = { actions.onCreate(account.accountId, it, parentId) },
        onDismiss = onDismiss,
        problem = { folderNameProblem(it, parentId, account)?.message() },
    )
}

/**
 * Only the user's own folders can be renamed, moved, colored, or deleted; system folders and Read
 * later keep their place and icon.
 */
@Composable
private fun FolderActionsDialog(
    folder: MailFolder,
    account: AccountFolders,
    actions: FolderActions,
    onDialog: (FolderDialog?) -> Unit,
) {
    val id = account.accountId
    val choices =
        listOfNotNull(
            DialogAction("Open", { actions.onOpen(folder) }, primary = true),
            DialogAction("New folder inside", { onDialog(FolderDialog.Create(id, folder.id)) })
                .takeIf { canCreateIn(account, folder) },
            DialogAction("Rename", { onDialog(FolderDialog.Rename(id, folder.id)) })
                .takeIf { folder.isCustom && folder.rights.mayRename },
            DialogAction("Move", { onDialog(FolderDialog.Move(id, folder.id)) })
                .takeIf { folder.isCustom && folder.rights.mayRename && moveTargets(account, folder).size > 1 },
            DialogAction("Color", { onDialog(FolderDialog.Color(id, folder.id)) })
                .takeIf { actions.color(folder) != null },
            DialogAction("Delete", { onDialog(FolderDialog.Delete(id, folder.id)) })
                .takeIf { folder.isCustom && folder.rights.mayDelete },
        )
    ActionDialog(folder.name, choices, onDismiss = { onDialog(null) })
}

private fun folderOf(
    dialog: FolderDialog,
    account: AccountFolders,
): MailFolder? =
    when (dialog) {
        is FolderDialog.Actions -> account.folder(dialog.folderId)
        is FolderDialog.Rename -> account.folder(dialog.folderId)
        is FolderDialog.Move -> account.folder(dialog.folderId)
        is FolderDialog.Delete -> account.folder(dialog.folderId)
        is FolderDialog.Color -> account.folder(dialog.folderId)
        is FolderDialog.Create -> null
    }
