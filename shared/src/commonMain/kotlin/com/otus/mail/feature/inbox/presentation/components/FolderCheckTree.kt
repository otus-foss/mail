package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.otus.designsystem.shared.rows.components.CollapsibleTree
import com.otus.designsystem.shared.rows.elements.Caption
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickMarkView
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.presentation.SheetTreeFolder
import com.otus.mail.feature.inbox.presentation.sheetTree

/**
 * The user's own folders in the Folders sheet, as the Move to sheet lists them, with a checkbox
 * at the end of each row that adds the mail to the folder or takes it out; nothing when [folders]
 * is empty. [folders] must be in tree order. While [searching], every match shows flat with its
 * parents under its name.
 */
@Composable
internal fun FolderCheckTree(
    folders: List<MessageFolder>,
    searching: Boolean,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (folders.isEmpty()) return
    Caption("Folders", RowTextInset)
    CollapsibleTree(
        sheetTree(folders),
        { it.folder.id },
        { it.depth },
        expandAll = searching,
    ) { entry, expanded, onExpandedChange ->
        val ticked = entry.folder.id in checked
        FolderTreeRow(
            entry.treeEntry(searching),
            { onToggle(entry.folder.id) },
            expanded,
            onExpandedChange,
            Modifier.semantics { stateDescription = if (ticked) "In this folder" else "Not in this folder" },
        ) { PickMarkView(PickMark(ticked, multiple = true)) }
    }
}

private fun SheetTreeFolder.treeEntry(searching: Boolean): FolderTreeEntry {
    val shownParents = if (searching) folder.path.dropLast(1) else parents
    return FolderTreeEntry(
        folder.path.last(),
        folder.color,
        depth = if (searching) 0 else depth,
        description = shownParents.takeIf { it.isNotEmpty() }?.joinToString(" / "),
    )
}
