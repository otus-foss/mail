package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.DateRangePicker
import com.otus.designsystem.components.SelectionSheet
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import kotlinx.datetime.LocalDate

/** A sheet the filter panel opens over itself. */
internal enum class FilterSheet { Date, }

/** [sheet] over the filter panel, editing [state]'s [draft]. */
@Composable
internal fun FilterPanelSheet(
    sheet: FilterSheet,
    state: InboxFilterState,
    draft: InboxFilters,
    dateFormatter: DateFormatter,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        FilterSheet.Date -> {
            FilterDateSheet(draft, dateFormatter, { range -> state.edit { it.copy(date = range) } }, onDismiss)
        }
    }
}

/** Picks the filter panel's custom date range; one day picked is a range of that day. */
@Composable
private fun FilterDateSheet(
    draft: InboxFilters,
    dateFormatter: DateFormatter,
    onPick: (DateFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val range = draft.date as? DateFilter.Range
    var start by rememberSaveable { mutableStateOf(range?.from?.toString()) }
    var end by rememberSaveable { mutableStateOf(range?.to?.toString()) }
    SelectionSheet(
        title = "Date",
        onDismiss = onDismiss,
        onClear = {
            start = null
            end = null
        },
        onApply = {
            val first = start
            onPick(
                if (first ==
                    null
                ) {
                    DateFilter.Any
                } else {
                    DateFilter.Range(LocalDate.parse(first), LocalDate.parse(end ?: first))
                },
            )
            onDismiss()
        },
    ) {
        DateRangePicker(start, end, dateFormatter) { from, to ->
            start = from
            end = to
        }
    }
}
