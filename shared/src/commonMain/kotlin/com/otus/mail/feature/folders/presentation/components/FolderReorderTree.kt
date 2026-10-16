package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ReorderableTree
import com.otus.mail.feature.folders.presentation.FolderListRow
import com.otus.mail.feature.folders.presentation.hasChildren
import com.otus.mail.feature.folders.presentation.treeDrop
import com.otus.mail.feature.folders.presentation.visible

/**
 * [rows] as a tree whose parents fold their subfolders with the folder icon. Holding a row drags
 * it. Between rows it moves the folder there, and onto a row it nests the folder inside as the
 * first subfolder. A line shows where it lands, and only where the server would accept it. The
 * [collapsed] rows, by key, belong to the caller so they survive a configuration change.
 */
@Composable
fun FolderReorderTree(
    rows: List<FolderListRow>,
    collapsed: Set<String>,
    onCollapsedChange: (Set<String>) -> Unit,
    actions: FolderListActions,
) {
    val visible = rows.visible(collapsed)
    ReorderableTree(
        visible,
        key = { it.key },
        depth = { it.node.depth },
        onDrop = { from, target -> visible.treeDrop(from, target, actions.placeable)?.let(actions.onDrop) },
        canDrop = { from, target -> visible.treeDrop(from, target, actions.placeable)?.let(actions.canDrop) == true },
    ) { row ->
        FolderRow(
            row,
            actions.colorOf(row.folder),
            actions.rulesOf(row.folder),
            expanded = (row.key !in collapsed).takeIf { rows.hasChildren(row) },
            onExpandedChange = { open -> onCollapsedChange(if (open) collapsed - row.key else collapsed + row.key) },
            onClick = { actions.onOpen(row.folder) },
        )
    }
}
