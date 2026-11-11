package com.otusfoss.mail.core.messages.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.shared.controls.elements.LocalIconTint
import com.otusfoss.core.designsystem.shared.rows.components.RowDensity
import com.otusfoss.core.designsystem.shared.rows.elements.nestIndent
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.sidebar.elements.SidebarExpandButton
import com.otusfoss.core.designsystem.theme.colors.FolderColor
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens

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
                    if (expanded != null) SidebarExpandButton(folder.name, expanded, onExpandedChange)
                    end()
                }
            },
        )
    }
}
