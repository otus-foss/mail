package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.mail.folders.components.FolderOwnerLine
import com.otus.designsystem.mail.folders.components.FolderRuleSummary
import com.otus.designsystem.mail.folders.components.FolderSettingRow
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder_shared
import com.otus.designsystem.shared.actionbar.components.QuickActionBar
import com.otus.designsystem.shared.controls.elements.LocalIconTint
import com.otus.designsystem.shared.filters.components.RuleChipLine
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.reorder.components.ReorderableScope
import com.otus.designsystem.shared.sidebar.elements.SidebarFolder
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.feature.folders.presentation.FolderListRow
import com.otus.mail.feature.folders.presentation.folderIcon

/**
 * A folder in Settings › Folders & rules: its icon in the folder's color, the name, the first of its rules
 * as a compact chip with how many more there are, and who shares it. With subfolders, [expanded]
 * is set and the icon folds them. Tapping opens the folder, a long press opens its quick actions
 * at the pressed point, and the handle at the end drags it.
 */
@Composable
fun ReorderableScope.FolderRow(
    row: FolderListRow,
    expanded: Boolean?,
    onExpandedChange: (Boolean) -> Unit,
    actions: FolderListActions,
    modifier: Modifier = Modifier,
) {
    val folder = row.folder
    val rules = actions.rulesOf(folder)
    val shared = row.sharingLine()
    val menu = actions.menuOf(folder)
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    CompositionLocalProvider(LocalIconTint provides FolderColor.named(actions.colorOf(folder)).tint) {
        FolderSettingRow(
            SidebarFolder(
                folder.name,
                row.node.depth,
                icon = if (shared != null) Res.drawable.symbol_folder_shared else folderIcon(row.node),
            ),
            expanded,
            onExpandedChange,
            onClick = { actions.onOpen(folder) },
            modifier,
            onLongClick = { at: Offset -> menuAt = at }.takeIf { menu.isNotEmpty() },
            detail =
                if (rules.isEmpty() && shared == null) {
                    null
                } else {
                    { FolderRowDetail(rules, shared) }
                },
        )
        menuAt?.let { at -> QuickActionBar(menu, at, onDismiss = { menuAt = null }) }
    }
}

/** The first rule, or else who shares the folder: the row has room for one line. */
@Composable
private fun FolderRowDetail(
    rules: List<RuleChipLine>,
    shared: Pair<ChipAvatar, String>?,
) {
    val first = rules.firstOrNull()
    when {
        first != null -> FolderRuleSummary(first, "+${rules.size - 1}".takeIf { rules.size > 1 })
        shared != null -> FolderOwnerLine(shared.first, shared.second)
    }
}

/** The owner's avatar and "Shared by <owner> · can read"; null for a folder nobody shares. */
private fun FolderListRow.sharingLine(): Pair<ChipAvatar, String>? =
    sharedBy?.let { (owner, canEdit) ->
        owner.avatar() to "Shared by $owner · ${if (canEdit) "can edit" else "can read"}"
    }

private fun String.avatar(): ChipAvatar =
    generatedAvatar(this, takeIf { '@' in it }.orEmpty()).let { ChipAvatar(it.initials, it.colorIndex) }
