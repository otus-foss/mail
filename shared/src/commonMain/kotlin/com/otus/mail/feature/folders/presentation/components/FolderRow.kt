package com.otus.mail.feature.folders.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.FolderOwnerLine
import com.otus.designsystem.components.FolderRuleSummary
import com.otus.designsystem.components.FolderSettingRow
import com.otus.designsystem.components.ReorderableScope
import com.otus.designsystem.components.RuleChipLine
import com.otus.designsystem.elements.LocalIconTint
import com.otus.designsystem.elements.SidebarFolder
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder_shared
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.folders.presentation.FolderListRow
import com.otus.mail.feature.folders.presentation.folderIcon

/**
 * A folder in Settings › Folders: its icon in the folder's [color], the name, the first of its
 * [rules] as a compact chip with how many more there are, and who shares it. With subfolders,
 * [expanded] is set and the icon folds them. A long press or the handle at the end drags it.
 */
@Composable
fun ReorderableScope.FolderRow(
    row: FolderListRow,
    color: String?,
    rules: List<RuleChipLine>,
    expanded: Boolean?,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shared = row.sharingLine()
    CompositionLocalProvider(LocalIconTint provides FolderColor.named(color).tint) {
        FolderSettingRow(
            SidebarFolder(
                row.folder.name,
                row.node.depth,
                icon = if (shared != null) Res.drawable.symbol_folder_shared else folderIcon(row.node),
            ),
            expanded,
            onExpandedChange,
            onClick,
            modifier.dragOnLongPress(),
            handle = { DragHandle(row.folder.name) },
            detail =
                if (rules.isEmpty() && shared == null) {
                    null
                } else {
                    { FolderRowDetail(rules, shared) }
                },
        )
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
