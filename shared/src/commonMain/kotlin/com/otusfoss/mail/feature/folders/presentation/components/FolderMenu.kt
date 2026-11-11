package com.otusfoss.mail.feature.folders.presentation.components

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_delete
import com.otusfoss.core.designsystem.resources.symbol_drive_file_move
import com.otusfoss.core.designsystem.resources.symbol_edit
import com.otusfoss.core.designsystem.resources.symbol_palette
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.core.mailbox.MailFolder
import com.otusfoss.mail.feature.folders.presentation.FolderDialog
import com.otusfoss.mail.feature.folders.presentation.canMove
import com.otusfoss.mail.feature.folders.presentation.deletable
import com.otusfoss.mail.feature.folders.presentation.subfolderCount

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
