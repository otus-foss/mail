package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.DetailRow
import com.otus.designsystem.elements.Symbol
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.presentation.folderIcon

/** Maps a folder and its message counts into a shared indented row. */
@Composable
fun FolderRow(
    node: FolderNode,
    onClick: () -> Unit,
) {
    val folder = node.folder
    DetailRow(
        folder.name,
        listOf(folderCounts(folder.total, folder.unread)),
        onClick,
        depth = node.depth,
        maxLines = 1,
    ) { Symbol(folderIcon(folder.role)) }
}

private fun folderCounts(
    total: Long,
    unread: Long,
): String {
    val messages = if (total == 1L) "1 message" else "$total messages"
    return if (unread > 0) "$messages · $unread unread" else messages
}
