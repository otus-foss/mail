package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.ReorderableColumn
import com.otus.mail.feature.folders.presentation.FolderListRow
import com.otus.mail.feature.folders.presentation.hasChildren
import com.otus.mail.feature.folders.presentation.moveDrop
import com.otus.mail.feature.folders.presentation.nestDrop
import com.otus.mail.feature.folders.presentation.visible

/**
 * [rows] as a tree whose parents fold their subfolders with the folder icon. Holding a row drags
 * it: between rows it moves the folder there, and onto a row it nests the folder inside. The
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
    ReorderableColumn(
        visible,
        key = { it.key },
        onMove = { from, to -> visible.moveDrop(from, to, actions.placeable)?.let(actions.onDrop) },
        onNest = { from, onto -> visible.nestDrop(from, onto)?.let(actions.onDrop) },
    ) { row ->
        FolderRow(
            row,
            actions.colorOf(row.folder),
            actions.rulesOf(row.folder),
            expanded = (row.key !in collapsed).takeIf { rows.hasChildren(row) },
            onExpandedChange = { open -> onCollapsedChange(if (open) collapsed - row.key else collapsed + row.key) },
            onClick = { actions.onOpen(row.folder) },
            modifier = Modifier.dragOnLongPress(),
        )
    }
}
