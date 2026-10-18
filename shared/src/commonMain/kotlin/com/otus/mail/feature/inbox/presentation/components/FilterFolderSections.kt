package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_folder_open
import com.otus.designsystem.theme.FilterTokens
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.folderChips
import com.otus.mail.feature.inbox.presentation.key
import com.otus.mail.feature.inbox.presentation.toggle

/** Folder shortcuts and chosen folders, then All folders…, which opens the folder tree. */
@Composable
internal fun FolderSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
    onAllFolders: () -> Unit,
) {
    FilterSection("In folder") {
        options.folderChips(draft).forEach { folder ->
            FolderChip(folder, draft.folders.firstOrNull { it.key == folder.key }, options) {
                onEdit { it.toggle(folder) }
            }
        }
        FilterChip("All folders…", selected = false, onClick = onAllFolders, role = FilterChipRole.Button) {
            FolderGlyph(FolderColor.Default, icon = Res.drawable.symbol_folder_open, size = FilterTokens.chipIconSize)
        }
    }
}

/** Folders whose mail the list leaves out, such as Sent or Trash, with their subfolders. */
@Composable
internal fun SkipSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    FilterSection("Skip folders") {
        val keys = (options.skip + draft.skipFolders.map { it.key }).distinct()
        keys.mapNotNull(options::folder).forEach { folder ->
            val skipped = draft.skipFolders.firstOrNull { it.key == folder.key }
            FolderChip(folder, skipped, options) {
                val ref = FolderRef(folder.key.loginId, folder.key.mailboxId, includeSubfolders = folder.hasChildren)
                onEdit { it.withSkipped(ref, on = skipped == null) }
            }
        }
    }
}
