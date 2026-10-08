package com.otus.mail.feature.inbox.presentation.components

import com.otus.designsystem.theme.CalendarTokens
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One Monday-based timeline; only the outer limits can contain empty cells. */
internal class CalendarWeeks(
    val last: LocalDate,
) {
    val first = LocalDate(CalendarTokens.MIN_YEAR, Month.JANUARY, 1)
    private val firstMonday = first.minus(first.dayOfWeek.ordinal, DateTimeUnit.DAY)
    val count = firstMonday.daysUntil(last) / CalendarTokens.DAYS_PER_WEEK + 1

    fun indexOf(date: LocalDate): Int =
        (firstMonday.daysUntil(date.coerceIn(first, last)) / CalendarTokens.DAYS_PER_WEEK).coerceIn(0, count - 1)

    fun startOfWeek(index: Int): LocalDate =
        firstMonday.plus(index.coerceIn(0, count - 1) * CalendarTokens.DAYS_PER_WEEK, DateTimeUnit.DAY)

    // A mixed week adopts the month it enters, matching jumps to the week containing the first.
    fun visibleMonth(index: Int): LocalDate {
        val monday = startOfWeek(index)
        val endOfWeek = monday.plus(CalendarTokens.DAYS_PER_WEEK - 1, DateTimeUnit.DAY).coerceIn(first, last)
        return LocalDate(endOfWeek.year, endOfWeek.month, 1)
    }
}
