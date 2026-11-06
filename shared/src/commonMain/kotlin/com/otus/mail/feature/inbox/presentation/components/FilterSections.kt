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
import com.otus.designsystem.shared.controls.components.SegmentedToggle
import com.otus.designsystem.shared.controls.elements.Chip
import com.otus.designsystem.shared.controls.elements.ChipLook
import com.otus.designsystem.shared.controls.elements.Symbol
import com.otus.designsystem.shared.filters.components.FilterSection
import com.otus.designsystem.shared.overlays.components.onPressDown
import com.otus.designsystem.theme.tokens.controls.ControlTokens
import com.otus.designsystem.theme.tokens.selection.FilterTokens
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
                FilterSection.Status -> StatusSection(draft, onEdit)
                FilterSection.Quick -> QuickSection(draft, options, onEdit)
                FilterSection.People -> PeopleCard(draft, options, people, onEdit, area)
                FilterSection.Rows -> MetadataRows(draft, options, dateFormatter, onEdit)
                FilterSection.Sort -> SortSection(draft, onEdit)
            }
        }
    }
}

@Composable
private fun StatusSection(
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    SegmentSection("Status", ReadStatus.entries, draft.status, { it.label }) { status ->
        onEdit { it.copy(status = status) }
    }
}

@Composable
private fun PeopleCard(
    draft: InboxFilters,
    options: FilterOptions,
    people: PeopleSearches,
    onEdit: FilterEdit,
    area: PeopleCardArea,
) {
    PeopleSection(draft, options, people, onEdit, Modifier.onGloballyPositioned { area.card = it })
}

@Composable
private fun MetadataRows(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
) {
    PopupRows(draft, options, dateFormatter, onEdit)
    SegmentSection("Size", SizeFilter.entries, draft.size, { it.label }) { size ->
        onEdit { it.copy(size = size) }
    }
}

@Composable
private fun SortSection(
    draft: InboxFilters,
    onEdit: FilterEdit,
) {
    SegmentSection("Sort", FilterSort.entries, draft.sort, { it.label }) { sort ->
        onEdit { it.copy(sort = sort) }
    }
}

/** A section whose [choices] are the segments of one toggle, [selected] lit. */
@Composable
private fun <T> SegmentSection(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FilterSection(title) {
        SegmentedToggle(
            choices.map(label),
            choices.indexOf(selected),
            { onSelect(choices[it]) },
            height = ControlTokens.segmentHeight,
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
    // The mail types close the Mail group.
    QuickGroup.entries.forEach { group -> QuickFilterGroup(group, draft, options, onEdit) }
}

@Composable
private fun QuickFilterGroup(
    group: QuickGroup,
    draft: InboxFilters,
    options: FilterOptions,
    onEdit: FilterEdit,
) {
    val facets = options.quick.filter { it.group == group }
    val types = if (group == QuickGroup.Mail) TypeFacet.entries else emptyList()
    if (facets.isEmpty() && types.isEmpty()) return
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

@Composable
private fun QuickChip(
    label: String,
    icon: DrawableResource,
    on: Boolean,
    onClick: () -> Unit,
) {
    Chip(label, onClick, selected = on, look = ChipLook.Raised) { Symbol(icon, size = ControlTokens.chipIconSize) }
}
