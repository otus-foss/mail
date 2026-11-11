package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.mail.folders.components.FilterFolderRow
import com.otusfoss.core.designsystem.mail.folders.components.FolderCheck
import com.otusfoss.core.designsystem.mail.folders.components.TreeFolder
import com.otusfoss.core.designsystem.shared.controls.elements.Chip
import com.otusfoss.core.designsystem.shared.controls.elements.ChipLook
import com.otusfoss.core.designsystem.shared.controls.elements.ChipStyle
import com.otusfoss.core.designsystem.shared.folders.elements.FolderGlyph
import com.otusfoss.core.designsystem.shared.overlays.components.ListSheet
import com.otusfoss.core.designsystem.shared.rows.components.AccountGroupRow
import com.otusfoss.core.designsystem.shared.selection.components.PickerSearch
import com.otusfoss.core.designsystem.theme.tokens.controls.ControlTokens
import com.otusfoss.core.designsystem.theme.tokens.selection.FilterTokens
import com.otusfoss.mail.core.mailbox.FolderKey
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.presentation.FilterAccount
import com.otusfoss.mail.feature.inbox.presentation.FilterFolder
import com.otusfoss.mail.feature.inbox.presentation.FilterOptions
import com.otusfoss.mail.feature.inbox.presentation.check
import com.otusfoss.mail.feature.inbox.presentation.includeSubfolders
import com.otusfoss.mail.feature.inbox.presentation.key
import com.otusfoss.mail.feature.inbox.presentation.shownFolders
import com.otusfoss.mail.feature.inbox.presentation.toggle

/**
 * Every folder of the page's accounts as a checkable tree in a sheet, under account headers when
 * there are several. A search narrows it to matching folders with their parents' path. System
 * folders are listed unticked like the rest, and a note says they stay out until ticked. Each tick
 * changes the draft at once; Done, the scrim, or Back close the sheet.
 */
@Composable
internal fun FolderPickerSheet(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf(emptySet<FolderKey>()) }
    var foldedAccounts by remember { mutableStateOf(emptySet<String>()) }
    ListSheet("Folders", onDismiss, search = { PickerSearch(query, { query = it }, "Search folders") }) {
        options.accounts.forEach { account ->
            val open = account.id !in foldedAccounts
            if (options.accounts.size > 1) {
                AccountGroupRow(account.name, account.avatar, open, { shown ->
                    foldedAccounts = if (shown) foldedAccounts - account.id else foldedAccounts + account.id
                })
            }
            if (open) {
                FolderRows(account, query, collapsed, draft, options, onEdit) { key, fold ->
                    collapsed = if (fold) collapsed + key else collapsed - key
                }
            }
        }
    }
}

@Composable
private fun FolderRows(
    account: FilterAccount,
    query: String,
    collapsed: Set<FolderKey>,
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
    onCollapse: (FolderKey, Boolean) -> Unit,
) {
    val searching = query.isNotBlank()
    account.shownFolders(query, collapsed).forEach { folder ->
        val check = draft.check(folder, options)
        val path = folder.ancestors.takeIf { searching && it.isNotEmpty() }?.pathText()
        FilterFolderRow(
            TreeFolder(folder.name, if (searching) 0 else folder.depth, path),
            check,
            onCheck = { onEdit { it.toggle(folder) } },
            expanded = (folder.key !in collapsed).takeIf { folder.hasChildren && !searching },
            onExpandedChange = { onCollapse(folder.key, !it) },
        ) { FolderGlyph(folder.color, icon = folder.icon, size = ControlTokens.chipIconSize) }
        if (check == FolderCheck.On && folder.hasChildren) OnlyThisFolder(folder, draft, onEdit)
    }
}

/** Under a chosen parent: whether its subfolders count too. */
@Composable
private fun OnlyThisFolder(
    folder: FilterFolder,
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    val only = draft.folders.firstOrNull { it.key == folder.key }?.includeSubfolders == false
    Chip(
        "Only this folder",
        { onEdit { it.includeSubfolders(folder, include = only) } },
        Modifier.padding(start = FilterTokens.folderIndent * (folder.depth + 1) + FilterTokens.folderRowHeight),
        selected = only,
        style = ChipStyle(look = ChipLook.Raised),
    )
}
