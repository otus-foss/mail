package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.components.MonthNavigation
import com.otus.designsystem.components.MonthNavigationLabel
import com.otus.designsystem.components.MonthYearPicker
import com.otus.designsystem.theme.CalendarTokens
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
internal fun CalendarHeading(
    month: LocalDate,
    maxDate: LocalDate,
    onMonth: (LocalDate) -> Unit,
    onDate: (LocalDate, Boolean) -> Unit,
) {
    var choosingMonth by rememberSaveable { mutableStateOf(false) }
    if (choosingMonth) {
        MonthYearPicker(
            month.year,
            month.month.ordinal + 1,
            maxDate,
            onDismiss = { choosingMonth = false },
            onConfirm = { year, number, day ->
                val date = LocalDate(year, number, day)
                onMonth(date)
                onDate(date, false)
                choosingMonth = false
            },
        )
    }
    val monthLabel =
        month.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    MonthNavigation(
        label =
            MonthNavigationLabel(
                monthLabel,
                month.year.toString(),
                month.year * Month.entries.size + month.month.ordinal,
            ),
        onPrevious = { onMonth(month.minus(1, DateTimeUnit.MONTH)) },
        onChooseMonth = { choosingMonth = true },
        onNext = { onMonth(month.plus(1, DateTimeUnit.MONTH)) },
        previousEnabled = month.year > CalendarTokens.MIN_YEAR || month.month != Month.JANUARY,
        nextEnabled = month < LocalDate(maxDate.year, maxDate.month, 1),
    )
}
