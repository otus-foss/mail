package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.FilterRowCard
import com.otus.designsystem.components.FilterSection
import com.otus.designsystem.components.FilterValueRow
import com.otus.designsystem.components.PeopleLine
import com.otus.designsystem.components.SegmentedToggle
import com.otus.designsystem.elements.FilterChip
import com.otus.designsystem.elements.Symbol
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_account_circle
import com.otus.designsystem.resources.symbol_calendar_today
import com.otus.designsystem.resources.symbol_folder
import com.otus.designsystem.resources.symbol_tune
import com.otus.designsystem.theme.FilterTokens
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FilterSection
import com.otus.mail.feature.inbox.domain.FilterSort
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.ReadStatus
import com.otus.mail.feature.inbox.presentation.FilterOptions
import com.otus.mail.feature.inbox.presentation.FilterPage
import org.jetbrains.compose.resources.DrawableResource

/** Changes the filter panel's draft. */
internal typealias FilterEdit = ((InboxFilters) -> InboxFilters) -> Unit

/**
 * What the main page needs besides the draft: the chosen people as chips, a way to open a page,
 * and the page the user came back from, whose row takes focus again.
 */
internal class MainPageLinks(
    val senders: List<PickedPerson>,
    val recipients: List<PickedPerson>,
    val onOpen: (FilterPage) -> Unit,
    val returnedFrom: FilterPage?,
)

/** A filter page's column of sections, inset from the panel's sides. */
@Composable
internal fun FilterPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = FilterTokens.bodyInset),
        verticalArrangement = Arrangement.spacedBy(FilterTokens.sectionGap),
        content = content,
    )
}

/** The filter panel's main page: the sections the user's layout shows, in its order. */
@Composable
internal fun FilterSections(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    onEdit: FilterEdit,
    links: MainPageLinks,
) {
    FilterPageColumn {
        options.layout.sections.filter(options::offers).forEach { section ->
            when (section) {
                FilterSection.Status -> StatusSection(draft.status, onEdit)
                FilterSection.Quick -> QuickSection(draft, options, onEdit)
                FilterSection.People -> PeopleSection(links, onEdit)
                FilterSection.Rows -> PageRows(draft, options, dateFormatter, links)
                FilterSection.Sort -> SortSection(draft.sort, onEdit)
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
    FilterSection("Quick filters") {
        options.quick.forEach { facet ->
            val on = draft.has(facet)
            FilterChip(facet.label, on, { onEdit { it.with(facet, !on) } }) {
                Symbol(facet.icon, size = FilterTokens.chipIconSize)
            }
        }
    }
}

@Composable
private fun PeopleSection(
    links: MainPageLinks,
    onEdit: FilterEdit,
) {
    FilterSection("People") {
        FilterRowCard(
            listOf(
                { PeopleRow("From", "Add people…", links.senders, FilterPage.From, links, onEdit) },
                { PeopleRow("To", "Anyone", links.recipients, FilterPage.To, links, onEdit) },
            ),
        )
    }
}

@Composable
private fun PeopleRow(
    label: String,
    placeholder: String,
    people: List<PickedPerson>,
    page: FilterPage,
    links: MainPageLinks,
    onEdit: FilterEdit,
) {
    PeopleLine(
        label,
        people.map { it.recipient },
        placeholder,
        { links.onOpen(page) },
        { index -> onEdit(people[index].remove) },
        Modifier.focusRequester(rememberReturnFocus(links.returnedFrom == page)),
    )
}

/** The rows that open the Account, Folders, Date, and Metadata pages, on one card. */
@Composable
private fun PageRows(
    draft: InboxFilters,
    options: FilterOptions,
    dateFormatter: DateFormatter,
    links: MainPageLinks,
) {
    val date = FilterRowValue(dateValue(draft.date, dateFormatter), draft.date != DateFilter.Any)
    val rows =
        buildList {
            if (options.offersAccount) {
                add(
                    PageLink(
                        Res.drawable.symbol_account_circle,
                        "Account",
                        accountValue(draft, options),
                        FilterPage.Account,
                    ),
                )
            }
            if (options.folders) {
                add(PageLink(Res.drawable.symbol_folder, "Folders", foldersValue(draft, options), FilterPage.Folders))
            }
            add(PageLink(Res.drawable.symbol_calendar_today, "Date", date, FilterPage.Date))
            add(PageLink(Res.drawable.symbol_tune, "Metadata", metadataValue(draft), FilterPage.Metadata))
        }
    FilterRowCard(rows.map { row -> { PageRow(row, links) } })
}

/** A main page row that opens [page], showing its [value]. */
private class PageLink(
    val icon: DrawableResource,
    val title: String,
    val value: FilterRowValue,
    val page: FilterPage,
)

@Composable
private fun PageRow(
    row: PageLink,
    links: MainPageLinks,
) {
    FilterValueRow(
        row.icon,
        row.title,
        row.value.text,
        { links.onOpen(row.page) },
        Modifier.focusRequester(rememberReturnFocus(links.returnedFrom == row.page)),
        set = row.value.set,
        spokenValue = row.value.spoken,
    )
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

/** A focus requester that takes focus when its row appears, if the row opened the page the user [returned] from. */
@Composable
private fun rememberReturnFocus(returned: Boolean): FocusRequester {
    val requester = remember { FocusRequester() }
    // Runs after the header's own focus request, so the row keeps focus.
    LaunchedEffect(Unit) { if (returned) requester.runCatching { requestFocus() } }
    return requester
}
