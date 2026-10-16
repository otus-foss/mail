package com.otus.mail.feature.folders.presentation.components

import com.otus.mail.feature.folders.domain.MailFolder

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
