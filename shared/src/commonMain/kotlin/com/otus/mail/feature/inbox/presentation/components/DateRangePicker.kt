package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.otus.mail.core.dateformat.DateFormatter
import com.otus.mail.core.dateformat.currentLocalDate
import com.otus.mail.core.dateformat.formatRelative
import com.otus.mail.core.dateformat.formatRelativeRange
import com.otus.mail.designsystem.elements.ActionButton
import com.otus.mail.designsystem.theme.CalendarTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.SheetTokens
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

@Composable
internal fun DateRangePicker(
    start: String?,
    end: String?,
    dateFormatter: DateFormatter,
    onChange: (String?, String?) -> Unit,
) {
    val today = currentLocalDate()
    val initialMonth = LocalDate.parse((start ?: today.toString()).take(YEAR_MONTH_LENGTH) + "-01")
    val weeks = remember(today) { CalendarWeeks(today) }
    val listState = rememberLazyListState(weeks.indexOf(initialMonth))
    val scope = rememberCoroutineScope()
    val goToMonth: (LocalDate) -> Unit = { month ->
        scope.launch { listState.animateScrollToItem(weeks.indexOf(LocalDate(month.year, month.month, 1))) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val controlsHeight = maxHeight * CalendarTokens.CONTROLS_HEIGHT_FRACTION
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
            Column(
                Modifier.heightIn(max = controlsHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
            ) {
                DateRangeSummary(start, end, dateFormatter, today)
                DateRangeShortcuts(today, start, end) { first, last ->
                    goToMonth(today)
                    onChange(first, last)
                }
            }
            Box(Modifier.weight(1f)) {
                ContinuousCalendar(
                    state = listState,
                    start = start,
                    end = end,
                    weeks = weeks,
                    onMonth = goToMonth,
                    onRange = onChange,
                    onDate = { chosen, throughToday ->
                        val date = chosen.toString()
                        when {
                            chosen > today -> Unit
                            throughToday -> onChange(minOf(chosen, today).toString(), maxOf(chosen, today).toString())
                            start == null || end != null -> onChange(date, null)
                            date < start -> onChange(date, start)
                            else -> onChange(start, date)
                        }
                    },
                )
            }
        }
    }
}

private const val YEAR_MONTH_LENGTH = 7
private const val WEEK_LENGTH = 7
private const val THIRTY_DAYS = 30

@Composable
private fun DateRangeSummary(
    start: String?,
    end: String?,
    dateFormatter: DateFormatter,
    today: LocalDate,
) {
    Text(
        when {
            start == null -> "Tap dates or hold and drag to select."
            end == null -> "${dateFormatter.formatRelative(LocalDate.parse(start), today)} → Choose an end date"
            else -> dateFormatter.formatRelativeRange(LocalDate.parse(start), LocalDate.parse(end), today)
        },
        color = OtusColors.text2,
        maxLines = 2,
    )
}

@Composable
private fun DateRangeShortcuts(
    today: LocalDate,
    start: String?,
    end: String?,
    onChoose: (String, String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
    ) {
        listOf("Today" to 1, "Last 7 days" to WEEK_LENGTH, "Last 30 days" to THIRTY_DAYS).forEach { (label, days) ->
            val first = today.minus(days - 1, DateTimeUnit.DAY).toString()
            val last = today.toString()
            val active = start == first && end == last
            ActionButton(
                label,
                { onChoose(first, last) },
                modifier = Modifier.semantics { selected = active },
                primary = active,
            )
        }
    }
}
