package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.otus.mail.designsystem.components.ActionDialog
import com.otus.mail.designsystem.components.ChoiceDialog
import com.otus.mail.designsystem.components.DialogAction
import com.otus.mail.designsystem.components.InputDialog
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
            val paths = folderTree(account.folders).associate { it.folder.id to it.path }
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
            ActionDialog(
                title = "Delete ${folder.name}?",
                message =
                    "Only an empty folder without subfolders can be deleted, so no mail is lost. " +
                        "Rules that move mail here will deliver it to the inbox instead.",
                actions = listOf(DialogAction("Delete", { actions.onDelete(folder) }, primary = true)),
                onDismiss = close,
            )
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

/** Only the user's own folders can be renamed, moved, or deleted; system folders keep their place. */
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
        is FolderDialog.Create -> null
    }
