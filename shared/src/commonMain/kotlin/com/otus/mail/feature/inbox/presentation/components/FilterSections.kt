package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.components.onPressDown
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import com.otus.mail.feature.inbox.presentation.FilterOptions
import org.jetbrains.compose.resources.DrawableResource

/** Changes the filter panel's draft. */
internal typealias FilterEdit = ((InboxFilters) -> InboxFilters) -> Unit

/**
 * The filter panel's sections that the user's layout shows, in its order, inset from the panel's
 * sides. A press off the People card leaves its lines, which picks a typed address and closes
 * their matches.
 */
@Composable
internal fun FilterSections(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
    people: PeopleSearches,
) {
    val area = remember { PeopleCardArea() }
    val focus = LocalFocusManager.current
    Column(
        Modifier
            .onGloballyPositioned { area.page = it }
            .onPressDown { if (!area.holds(it)) focus.clearFocus() }
            .padding(horizontal = FilterTokens.bodyInset),
        verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
    ) {
        options.layout.sections.forEach { section ->
            when (section) {
                FilterSection.Status -> {
                    StatusSection(draft.status, onEdit)
                }

                FilterSection.Quick -> {
                    QuickSection(draft, options, onEdit)
                }

                FilterSection.People -> {
                    val card = Modifier.onGloballyPositioned { area.card = it }
                    PeopleSection(draft, options, people, onEdit, card)
                }

                FilterSection.Rows -> {
                    PopupRows(draft, options, dateFormatter, onEdit)
                    SizeSection(draft.size, onEdit)
                }

                FilterSection.Sort -> {
                    SortSection(draft.sort, onEdit)
                }
            }
        }
    }
}

@Composable
private fun StatusSection(
    status: ReadStatus,
    onEdit: FilterEdit,
) {
    FilterSection("Status") {
        SegmentedToggle(
            ReadStatus.entries.map { it.label },
            ReadStatus.entries.indexOf(status),
            { index -> onEdit { it.copy(status = ReadStatus.entries[index]) } },
            height = FilterTokens.segmentHeight,
        )
    }
}

@Composable
private fun QuickSection(
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    // Each group keeps the user's order; a group whose chips are all hidden leaves no caption.
    // Each group keeps the user's order; a group whose chips are all hidden leaves no caption.
    // The mail types close the Mail group.
    QuickGroup.entries.forEach { group ->
        val facets = options.quick.filter { it.group == group }
        val types = if (group == QuickGroup.Mail) TypeFacet.entries else emptyList()
        if (facets.isNotEmpty() || types.isNotEmpty()) {
            FilterSection(group.label) {
                facets.forEach { facet ->
                    val on = draft.has(facet)
                    QuickChip(facet.label, facet.icon, on) { onEdit { it.with(facet, !on) } }
                }
                types.forEach { facet ->
                    val on = draft.has(facet)
                    QuickChip(facet.label, facet.icon, on) { onEdit { it.with(facet, !on) } }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: DrawableResource,
    on: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(label, on, onClick) { Symbol(icon, size = FilterTokens.chipIconSize) }
}

@Composable
private fun SizeSection(
    size: SizeFilter,
    onEdit: FilterEdit,
) {
    FilterSection("Size") {
        SegmentedToggle(
            SizeFilter.entries.map { it.label },
            SizeFilter.entries.indexOf(size),
            { index -> onEdit { it.copy(size = SizeFilter.entries[index]) } },
            height = FilterTokens.segmentHeight,
        )
    }
}

@Composable
private fun SortSection(
    sort: FilterSort,
    onEdit: FilterEdit,
) {
    FilterSection("Sort") {
        SegmentedToggle(
            listOf("Newest first", "Oldest first"),
            FilterSort.entries.indexOf(sort),
            { index -> onEdit { it.copy(sort = FilterSort.entries[index]) } },
            height = FilterTokens.segmentHeight,
        )
    }
}
