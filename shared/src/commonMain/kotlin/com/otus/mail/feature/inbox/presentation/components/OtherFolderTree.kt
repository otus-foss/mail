package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.CollapsibleTree
import com.otus.designsystem.components.FilterFolderRow
import com.otus.designsystem.components.FolderCheck
import com.otus.designsystem.components.FolderNameGlyph
import com.otus.designsystem.components.TreeFolder
import com.otus.designsystem.theme.PanelTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.presentation.SheetTreeFolder
import com.otus.mail.feature.inbox.presentation.sheetTree

/**
 * The Folders sheet's "Other folders" as a collapsible tree with a checkbox per folder; nothing
 * when [folders] is empty. [folders] must be in tree order. While [searching], every match shows
 * flat with its full path.
 */
@Composable
internal fun OtherFolderTree(
    folders: List<MessageFolder>,
    searching: Boolean,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (folders.isEmpty()) return
    Column {
        SheetCaption("Other folders", Modifier.padding(horizontal = SheetTokens.inset))
        CollapsibleTree(
            sheetTree(folders),
            { it.folder.id },
            { it.depth },
            expandAll = searching,
        ) { entry, expanded, onExpandedChange ->
            val folder = entry.folder
            FilterFolderRow(
                entry.treeFolder(searching),
                if (folder.id in checked) FolderCheck.On else FolderCheck.Off,
                onCheck = { onToggle(folder.id) },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
            ) { FolderNameGlyph(folderName(folder), PanelTokens.iconSize) }
        }
    }
}

private fun SheetTreeFolder.treeFolder(searching: Boolean): TreeFolder {
    val shownParents = if (searching) folder.path.dropLast(1) else parents
    return TreeFolder(
        folder.path.last(),
        depth = if (searching) 0 else depth,
        path = shownParents.takeIf { it.isNotEmpty() }?.joinToString(" / "),
    )
}
