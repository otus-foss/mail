package com.otus.mail.feature.folders.presentation.components

import com.otus.mail.feature.folders.domain.MailFolder
import com.otus.mail.feature.folders.presentation.FolderColorsState
import com.otus.mail.feature.folders.presentation.FoldersState

/**
 * Everything the folder popups can ask for. Folder changes go to the server. [color] offers a
 * folder's color, kept on this device, or null for a folder that cannot have one.
 */
class FolderActions(
    val onCreate: (accountId: String, name: String, parentId: String?) -> Unit,
    val onRename: (MailFolder, String) -> Unit,
    val onDelete: (MailFolder) -> Unit,
    /** Moves the folder under the parent with this id, or to the top level for null. */
    val onMove: (MailFolder, parentId: String?) -> Unit,
    val color: (MailFolder) -> FolderColorChoice?,
)

/**
 * A folder's stored color name, [current], shown even while the Folder colors switch is off, and
 * [onPick], which saves a new one or null for the default.
 */
class FolderColorChoice(
    val current: String?,
    val onPick: (String?) -> Unit,
)

/** Creating, renaming, deleting, and moving go to the server; the icon color stays on this device. */
fun folderActions(
    folders: FoldersState,
    colors: FolderColorsState,
) = FolderActions(
    onCreate = { accountId, name, parentId -> folders.create(accountId, name, parentId) },
    onRename = folders::rename,
    onDelete = folders::delete,
    onMove = { folder, parentId -> folders.place(folder, parentId) },
    color = { folder ->
        FolderColorChoice(colors.colors.of(folder)) { colors.setColor(listOf(folder), it) }
            .takeIf { colors.colorable(folder) && folder.sharedIn == null }
    },
)
