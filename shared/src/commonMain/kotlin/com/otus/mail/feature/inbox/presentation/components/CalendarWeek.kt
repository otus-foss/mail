package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.otus.mail.designsystem.elements.CalendarDay
import com.otus.mail.designsystem.elements.CalendarDaySelection
import com.otus.mail.designsystem.elements.calendarMonthBackground
import com.otus.mail.designsystem.theme.CalendarTokens
import com.otus.mail.designsystem.theme.PanelTokens
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

@Composable
internal fun CalendarWeek(
    firstDay: LocalDate,
    start: String?,
    end: String?,
    onDate: (LocalDate, Boolean) -> Unit,
    targets: CalendarDragTargets,
) {
    val dates = remember(firstDay) { List(CalendarTokens.DAYS_PER_WEEK) { firstDay.plus(it, DateTimeUnit.DAY) } }
    DisposableEffect(firstDay, targets) {
        onDispose { targets.rows.remove(firstDay) }
    }
    val background =
        remember(firstDay, targets) { monthBackgrounds(firstDay, dates.filter { it in targets.allowedDates }) }
    Row(background.fillMaxWidth().onGloballyPositioned { targets.rows[firstDay] = it }) {
        dates.forEachIndexed { weekday, date ->
            if (date in targets.allowedDates) {
                CalendarDateCell(date, start, end, weekday, onDate, Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f).heightIn(min = PanelTokens.touchTarget))
            }
        }
    }
}

private fun monthBackgrounds(
    monday: LocalDate,
    dates: List<LocalDate>,
): Modifier =
    dates.distinctBy { it.month }.fold<LocalDate, Modifier>(Modifier) { modifier, date ->
        val first = LocalDate(date.year, date.month, 1)
        val days = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
        val week = (first.daysUntil(monday) + first.dayOfWeek.ordinal) / CalendarTokens.DAYS_PER_WEEK
        val color =
            if (date.month.ordinal % 2 == 0) CalendarTokens.monthBackground else CalendarTokens.alternateMonthBackground
        modifier.calendarMonthBackground(first.dayOfWeek.ordinal, days, week, color)
    }

@Composable
private fun CalendarDateCell(
    date: LocalDate,
    start: String?,
    end: String?,
    weekday: Int,
    onDate: (LocalDate, Boolean) -> Unit,
    modifier: Modifier,
) {
    val value = remember(date) { date.toString() }
    val label = remember(date) { date.day.toString() }
    val monthLabel = remember(date) { monthLabel(date) }
    val last = end ?: start
    val active = start != null && value >= start && value <= last.orEmpty()
    CalendarDay(
        label,
        value,
        selection =
            CalendarDaySelection(
                inRange = active,
                endpoint = value == start || value == end,
                connectsPrevious = active && value != start && weekday > 0,
                connectsNext = active && value != last && weekday < CalendarTokens.DAYS_PER_WEEK - 1,
            ),
        monthLabel = monthLabel,
        onClick = { onDate(date, false) },
        onDoubleClick = { onDate(date, true) },
        modifier =
            modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction("Select through today") {
                            onDate(date, true)
                            true
                        },
                    )
            },
    )
}

private fun monthLabel(date: LocalDate): String? =
    if (date.day == 1) {
        date.month.name
            .take(CalendarTokens.MONTH_LABEL_LENGTH)
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    } else {
        null
    }
