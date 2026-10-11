package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.folders.domain.FolderNode
import com.otus.mail.feature.folders.presentation.folderIcon

/** A folder and its message counts as a settings row, indented by its nesting depth. */
@Composable
fun FolderRow(
    node: FolderNode,
    onClick: () -> Unit,
) {
    val folder = node.folder
    SettingRow(
        folder.name,
        onClick,
        Modifier.padding(start = PageTokens.nestIndent * node.depth),
        description = folderCounts(folder.total, folder.unread),
        icon = folderIcon(folder.role),
    )
}

private fun folderCounts(
    total: Long,
    unread: Long,
): String {
    val messages = if (total == 1L) "1 message" else "$total messages"
    return if (unread > 0) "$messages · $unread unread" else messages
}
