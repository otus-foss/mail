package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarExpandButton
import com.otus.designsystem.elements.nestIndent
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.theme.FolderColor
import com.otus.designsystem.theme.PageTokens

/**
 * One of the user's own folders in a sheet's folder tree: its [name], its [color], [depth] levels
 * in, and an optional grey [description] under it, such as the parents of a folder whose parent is
 * not listed.
 */
internal data class FolderTreeEntry(
    val name: String,
    val color: String?,
    val depth: Int,
    val description: String? = null,
)

/**
 * A row of a sheet's folder tree, as the Move to and Folders sheets show it: indented by depth,
 * with the folder's colored icon, then the chevron that collapses its nested folders when it has
 * any ([expanded] not null), then [end], such as a checkbox. The whole row runs [onClick].
 */
@Composable
internal fun FolderTreeRow(
    folder: FolderTreeEntry,
    onClick: (() -> Unit)?,
    expanded: Boolean?,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    end: @Composable () -> Unit = {},
) {
    CompositionLocalProvider(LocalIconTint provides FolderColor.named(folder.color).tint) {
        SettingRow(
            folder.name,
            onClick,
            modifier.nestIndent(PageTokens.nestIndent, folder.depth),
            description = folder.description,
            icon = Res.drawable.symbol_folder,
            density = RowDensity.Compact,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    expanded?.let { SidebarExpandButton(folder.name, it, onExpandedChange) }
                    end()
                }
            },
        )
    }
}
