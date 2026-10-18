package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.elements.FolderGlyph
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_folder_open
import com.otus.designsystem.theme.FilterTokens
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.folderChips
import com.otus.mail.feature.inbox.presentation.key
import com.otus.mail.feature.inbox.presentation.toggle

/**
 * The filter panel's main page: Show, Sent to while the page has more than one address, In
 * folder where the page can filter by folder, Date, and Sort. [onEdit] changes the draft.
 */
@Composable
internal fun FilterSections(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    onEdit: ((InboxFilters) -> InboxFilters) -> Unit,
    onAllFolders: () -> Unit,
    onCustomDate: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = FilterTokens.bodyInset),
        verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
    ) {
        ShowSection(draft, options.readLater, onEdit)
        if (options.addresses.size > 1) SentToSection(draft, options, onEdit)
        if (options.folders) FolderSection(draft, options, onEdit, onAllFolders)
        DateSection(draft.date, dateFormatter, onEdit, onCustomDate)
        SegmentedToggle(
            listOf("Newest first", "Oldest first"),
            FilterSort.entries.indexOf(draft.sort),
            { index -> onEdit { it.copy(sort = FilterSort.entries[index]) } },
            height = FilterTokens.segmentHeight,
        )
    }
}

@Composable
private fun ShowSection(
    draft: InboxFilters,
    readLater: Boolean,
    onEdit: ((InboxFilters) -> InboxFilters) -> Unit,
) {
    FilterSection("Show") {
        ShowFacet.entries.filter { readLater || it != ShowFacet.ReadLater }.forEach { facet ->
            val on = draft.has(facet)
            FilterChip(facet.label, on, { onEdit { it.with(facet, !on) } }) {
                Symbol(facet.icon, size = FilterTokens.chipIconSize)
            }
        }
    }
}

@Composable
private fun SentToSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: ((InboxFilters) -> InboxFilters) -> Unit,
) {
    FilterSection("Sent to") {
        options.addresses.forEach { (address, avatar) ->
            val on = address in draft.sentTo
            FilterChip(address.address, on, {
                onEdit { it.copy(sentTo = if (on) it.sentTo - address else it.sentTo + address) }
            }) { PersonAvatar(avatar, FilterTokens.chipAvatarSize) }
        }
    }
}

@Composable
private fun FolderSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: ((InboxFilters) -> InboxFilters) -> Unit,
    onAllFolders: () -> Unit,
) {
    FilterSection("In folder") {
        options.folderChips(draft).forEach { folder ->
            FolderChip(folder, draft.folders.firstOrNull { it.key == folder.key }, options) {
                onEdit { it.toggle(folder) }
            }
        }
        FilterChip("All folders…", selected = false, onClick = onAllFolders, role = FilterChipRole.Button) {
            FolderGlyph(FolderColor.Default, Res.drawable.symbol_folder_open)
        }
    }
}

@Composable
private fun DateSection(
    date: DateFilter,
    dateFormatter: DateFormatter,
    onEdit: ((InboxFilters) -> InboxFilters) -> Unit,
    onCustomDate: () -> Unit,
) {
    FilterSection("Date") {
        listOf(DateFilter.Any, DateFilter.Today, DateFilter.Last7, DateFilter.Last30).forEach { preset ->
            FilterChip(preset.label, date == preset, { onEdit { it.copy(date = preset) } }, role = FilterChipRole.Radio)
        }
        val custom = date as? DateFilter.Range
        FilterChip(
            custom?.let { rangeLabel(it, dateFormatter) } ?: "Custom…",
            custom != null,
            onCustomDate,
            role = FilterChipRole.Radio,
        ) { Symbol(Res.drawable.symbol_calendar_today, size = FilterTokens.chipIconSize) }
    }
}
