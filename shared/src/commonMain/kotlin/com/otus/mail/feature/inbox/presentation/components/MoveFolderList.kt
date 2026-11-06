package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.otus.designsystem.shared.rows.components.CollapsibleTree
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.selection.elements.currentPlace
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The user's own folders under a caption, indented by nesting; nothing when none match. A folder
 * with nested folders collapses them with the chevron at the end of its row, while the row itself
 * still moves the message. Everything starts expanded, and a search shows every match. The folder
 * the message is in, [here], has the sidebar's current-folder fill and does not move it.
 */
@Composable
internal fun MoveFolderList(
    folders: List<MoveTarget>,
    here: String?,
    searching: Boolean,
    onMove: (MoveTarget) -> Unit,
) {
    if (folders.isEmpty()) return
    Caption("Folders", RowTextInset)
    CollapsibleTree(
        folders,
        { it.folderId },
        { it.depth },
        expandAll = searching,
    ) { folder, expanded, onExpandedChange ->
        val isHere = folder.folderId == here
        FolderTreeRow(
            FolderTreeEntry(folder.name, folder.color, folder.depth),
            { onMove(folder) }.takeUnless { isHere },
            expanded,
            onExpandedChange,
            if (isHere) Modifier.currentPlace().semantics { stateDescription = "Current folder" } else Modifier,
        )
    }
}
