package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.AccountGroupRow
import com.otus.designsystem.components.FilterFolderRow
import com.otus.designsystem.components.FolderCheck
import com.otus.designsystem.components.PickerSearch
import com.otus.designsystem.components.ScopeSheet
import com.otus.designsystem.components.TreeFolder
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterAccount
import com.otus.mail.feature.inbox.presentation.FilterFolder
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FolderKey
import com.otus.mail.feature.inbox.presentation.check
import com.otus.mail.feature.inbox.presentation.includeSubfolders
import com.otus.mail.feature.inbox.presentation.key
import com.otus.mail.feature.inbox.presentation.shownFolders
import com.otus.mail.feature.inbox.presentation.toggle

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
    ScopeSheet(
        "Folders",
        onDismiss,
        search = { PickerSearch(query, { query = it }, "Search folders") },
        footnote = "Drafts, Sent, Junk and Trash are left out unless you tick them.",
    ) {
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
        val path = folder.ancestors.takeIf { searching && it.isNotEmpty() }?.joinToString(" / ")
        FilterFolderRow(
            TreeFolder(folder.name, if (searching) 0 else folder.depth, path),
            check,
            onCheck = { onEdit { it.toggle(folder) } },
            expanded = (folder.key !in collapsed).takeIf { folder.hasChildren && !searching },
            onExpandedChange = { onCollapse(folder.key, !it) },
        ) { FolderGlyph(folder.color, icon = folder.icon, size = FilterTokens.chipIconSize) }
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
    FilterChip(
        "Only this folder",
        only,
        { onEdit { it.includeSubfolders(folder, include = only) } },
        Modifier.padding(start = FilterTokens.folderIndent * (folder.depth + 1) + FilterTokens.folderRowHeight),
    )
}
