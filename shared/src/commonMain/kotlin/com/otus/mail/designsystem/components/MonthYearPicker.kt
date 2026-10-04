package com.otus.mail.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.otus.mail.designsystem.elements.ActionButton
import com.otus.mail.designsystem.elements.SelectionRow
import com.otus.mail.designsystem.theme.CalendarTokens
import com.otus.mail.designsystem.theme.OtusColors
import com.otus.mail.designsystem.theme.SheetTokens
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Scrollable date choices. The caller decides how the confirmed date is used. */
@Composable
fun MonthYearPicker(
    initialYear: Int,
    initialMonth: Int,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
    initialDay: Int = 1,
) {
    var year by rememberSaveable { mutableIntStateOf(initialYear.coerceIn(CalendarTokens.MIN_YEAR, maxDate.year)) }
    var month by rememberSaveable { mutableIntStateOf(initialMonth) }
    var day by rememberSaveable { mutableIntStateOf(initialDay) }
    val lastMonth = if (year == maxDate.year) maxDate.month.ordinal + 1 else MONTH_NAMES.size
    val selectedMonth = month.coerceIn(1, lastMonth)
    val lastDay = remember(year, selectedMonth, maxDate) { lastAllowedDay(year, selectedMonth, maxDate) }
    val days = remember(lastDay) { (1..lastDay).map(Int::toString) }
    val selectedDay = day.coerceIn(1, lastDay)
    val years = remember(maxDate.year) { (maxDate.year downTo CalendarTokens.MIN_YEAR).map(Int::toString) }
    DatePickerDialog(onDismiss) {
        MonthYearTitle()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
            MonthYearColumn(
                "Day",
                days,
                selectedDay - 1,
                { day = it + 1 },
                Modifier.weight(1f),
            )
            MonthYearColumn(
                "Month",
                MONTH_NAMES.take(lastMonth),
                selectedMonth - 1,
                {
                    month = it + 1
                    day = day.coerceAtMost(lastAllowedDay(year, month, maxDate))
                },
                Modifier.weight(CalendarTokens.MONTH_COLUMN_WEIGHT),
            )
            MonthYearColumn(
                "Year",
                years,
                maxDate.year - year,
                {
                    year = maxDate.year - it
                    month =
                        month.coerceAtMost(if (year == maxDate.year) maxDate.month.ordinal + 1 else MONTH_NAMES.size)
                    day = day.coerceAtMost(lastAllowedDay(year, month, maxDate))
                },
                Modifier.weight(1f),
            )
        }
        MonthYearActions(onDismiss) { onConfirm(year, selectedMonth, selectedDay) }
    }
}

private fun lastAllowedDay(
    year: Int,
    month: Int,
    maxDate: LocalDate,
): Int =
    if (year == maxDate.year && month == maxDate.month.ordinal + 1) {
        maxDate.day
    } else {
        LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
    }

@Composable
private fun MonthYearColumn(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        Text(title, color = OtusColors.text2)
        val scroll = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
        LazyColumn(Modifier.selectableGroup().semantics { contentDescription = "$title choices" }, state = scroll) {
            items(options.size, key = { it }) { index ->
                SelectionRow(options[index], index == selected, { onSelect(index) }, singleChoice = true)
            }
        }
    }
}

private val MONTH_NAMES =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

@Composable
private fun MonthYearActions(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
        ActionButton("Cancel", onDismiss, Modifier.weight(1f))
        ActionButton("Go", onConfirm, Modifier.weight(1f), primary = true)
    }
}

@Composable
private fun MonthYearTitle() {
    Text("Choose date", Modifier.semantics { heading() }, fontSize = SheetTokens.titleSize)
}
