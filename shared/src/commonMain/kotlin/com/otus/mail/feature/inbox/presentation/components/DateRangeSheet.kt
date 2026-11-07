package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.controls.elements.UiAction
import com.otus.designsystem.shared.dates.components.DateRangePicker
import com.otus.designsystem.shared.overlays.components.ListSheet
import com.otus.mail.core.messages.domain.DateFilter
import kotlinx.datetime.LocalDate

/**
 * Picks a range of days, starting from [range]. Done hands [onDone] the picked range, a single day
 * when only one is tapped, or null after Clear, for any time.
 */
@Composable
internal fun DateRangeSheet(
    range: DateFilter.Range?,
    dateFormatter: DateFormatter,
    onDone: (DateFilter.Range?) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by rememberSaveable { mutableStateOf(range?.from?.toString()) }
    var end by rememberSaveable { mutableStateOf(range?.to?.toString()) }
    ListSheet(
        "Date",
        onDismiss,
        done =
            UiAction("Done", onClick = {
                onDone(start?.let { DateFilter.Range(LocalDate.parse(it), LocalDate.parse(end ?: it)) })
                onDismiss()
            }),
        onClear = {
            start = null
            end = null
        },
        scrolls = true,
    ) {
        DateRangePicker(start, end, dateFormatter) { first, last ->
            start = first
            end = last
        }
    }
}
