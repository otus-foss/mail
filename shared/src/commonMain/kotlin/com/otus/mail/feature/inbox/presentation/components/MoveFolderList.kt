package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.CollapsibleTree
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarExpandButton
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.theme.FolderColor
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.inbox.presentation.MoveTarget

/**
 * The user's own folders under a caption, indented by nesting; nothing when none match. A folder
 * with nested folders collapses them with the chevron at the end of its row, while the row itself
 * still moves the message. Everything starts expanded, and a search shows every match.
 */
@Composable
internal fun MoveFolderList(
    folders: List<MoveTarget>,
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
        CompositionLocalProvider(LocalIconTint provides FolderColor.named(folder.color).tint) {
            SettingRow(
                folder.name,
                { onMove(folder) },
                Modifier.padding(start = PageTokens.nestIndent * folder.depth),
                icon = Res.drawable.symbol_folder,
                density = RowDensity.Compact,
                trailing = { expanded?.let { SidebarExpandButton(folder.name, it, onExpandedChange) } },
            )
        }
    }
}
