package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.FilterChipRole
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.presentation.FilterOptions

/** Changes the filter panel's draft. */
internal typealias FilterEdit = ((InboxFilters) -> InboxFilters) -> Unit

/** What the filter panel's sections can open: the folder page, the date range, and a typed sender. */
internal class FilterPanelPages(
    val onAllFolders: () -> Unit,
    val onCustomDate: () -> Unit,
    val onAddSender: () -> Unit,
)

/**
 * The filter panel's main page: the sections the user's layout shows, in its order. Sections
 * that have nothing to offer on this page, such as Sent to with one address, stay hidden.
 */
@Composable
internal fun FilterSections(
    draft: InboxFilters,
    options: FilterOptions,
    senders: List<MailSender>,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
    pages: FilterPanelPages,
) {
    Column(
        Modifier.padding(horizontal = FilterTokens.bodyInset),
        verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
    ) {
        options.layout.sections.forEach { section ->
            when (section) {
                FilterSection.Show -> {
                    ShowSection(draft, options, onEdit)
                }

                FilterSection.From -> {
                    FromSection(draft, senders, onEdit, pages.onAddSender)
                }

                FilterSection.Accounts -> {
                    if (options.accounts.size > 1) AccountsSection(draft, options, onEdit)
                }

                FilterSection.SentTo -> {
                    if (options.addresses.size > 1) SentToSection(draft, options, onEdit)
                }

                FilterSection.ReceivedAs -> {
                    if (options.addresses.isNotEmpty()) ReceivedAsSection(draft, onEdit)
                }

                FilterSection.InFolder -> {
                    if (options.folders) FolderSection(draft, options, onEdit, pages.onAllFolders)
                }

                FilterSection.SkipFolders -> {
                    if (options.folders &&
                        options.skip.isNotEmpty()
                    ) {
                        SkipSection(draft, options, onEdit)
                    }
                }

                FilterSection.Date -> {
                    DateSection(draft.date, dateFormatter, onEdit, pages.onCustomDate)
                }

                FilterSection.Size -> {
                    SizeSection(draft.size, onEdit)
                }

                FilterSection.Sort -> {
                    SortToggle(draft.sort, onEdit)
                }
            }
        }
    }
}

@Composable
private fun ShowSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    val facets = options.layout.show.filter { options.readLater || it != ShowFacet.ReadLater }
    if (facets.isEmpty()) return
    FilterSection("Show") {
        facets.forEach { facet ->
            val on = draft.has(facet)
            FilterChip(facet.label, on, { onEdit { it.with(facet, !on) } }) {
                Symbol(facet.icon, size = FilterTokens.chipIconSize)
            }
        }
    }
}

@Composable
private fun DateSection(
    date: DateFilter,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
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

@Composable
private fun SizeSection(
    size: SizeFilter,
    onEdit: FilterEdit,
) {
    FilterSection("Size") {
        SizeFilter.entries.forEach { choice ->
            FilterChip(choice.label, size == choice, { onEdit { it.copy(size = choice) } }, role = FilterChipRole.Radio)
        }
    }
}

@Composable
private fun SortToggle(
    sort: FilterSort,
    onEdit: FilterEdit,
) {
    SegmentedToggle(
        listOf("Newest first", "Oldest first"),
        FilterSort.entries.indexOf(sort),
        { index -> onEdit { it.copy(sort = FilterSort.entries[index]) } },
        height = FilterTokens.segmentHeight,
    )
}
