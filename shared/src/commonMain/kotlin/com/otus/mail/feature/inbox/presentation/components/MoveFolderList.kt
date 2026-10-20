package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.otus.designsystem.components.CollapsibleTree
import com.otus.designsystem.theme.OtusAlpha
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The user's own folders under a caption, indented by nesting; nothing when none match. A folder
 * with nested folders collapses them with the chevron at the end of its row, while the row itself
 * still moves the message. Everything starts expanded, and a search shows every match. The folder
 * the message is in, [here], shows dimmed with "here now" and does not move it.
 */
@Composable
internal fun MoveFolderList(
    folders: List<MoveTarget>,
    here: String?,
    searching: Boolean,
    onMove: (MoveTarget) -> Unit,
) {
    if (folders.isEmpty()) return
    SheetCaption("Folders", RowTextInset)
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
            Modifier.alpha(if (isHere) OtusAlpha.DISABLED else 1f),
        ) {
            if (isHere) Text(HERE_NOW, color = OtusColors.text3, fontSize = SheetTokens.detailSize)
        }
    }
}

private const val HERE_NOW = "here now"
