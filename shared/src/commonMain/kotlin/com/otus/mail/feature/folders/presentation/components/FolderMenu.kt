package com.otus.mail.feature.folders.presentation.components

import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_delete
import com.otus.designsystem.resources.symbol_drive_file_move
import com.otus.designsystem.resources.symbol_edit
import com.otus.designsystem.resources.symbol_palette
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.mail.feature.folders.domain.AccountFolders
import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FolderDialog
import com.otus.mail.feature.folders.presentation.canMove
import com.otus.mail.feature.folders.presentation.deletable
import com.otus.mail.feature.folders.presentation.subfolderCount

/**
 * The quick actions a long press on [folder] offers, each opening its popup through [onDialog]:
 * Rename, Move into, Color when it is [colorable], and Delete once it is empty. Only the user's
 * own folders have any; a shared mailbox has none.
 */
internal fun AccountFolders.folderMenu(
    folder: MailFolder,
    colorable: Boolean,
    onDialog: (FolderDialog) -> Unit,
): List<UiAction> {
    if (!folder.isCustom || folder.sharedIn != null) return emptyList()
    val id = accountId
    return listOfNotNull(
        UiAction("Rename", Res.drawable.symbol_edit) { onDialog(FolderDialog.Rename(id, folder.id)) }
            .takeIf { folder.rights.mayRename },
        UiAction("Move into", Res.drawable.symbol_drive_file_move) { onDialog(FolderDialog.Move(id, folder.id)) }
            .takeIf { canMove(folder) },
        UiAction("Color", Res.drawable.symbol_palette) { onDialog(FolderDialog.Color(id, folder.id)) }
            .takeIf { colorable },
        UiAction("Delete", Res.drawable.symbol_delete) { onDialog(FolderDialog.Delete(id, folder.id)) }
            .takeIf { folder.deletable(subfolderCount(folder)) },
    )
}
