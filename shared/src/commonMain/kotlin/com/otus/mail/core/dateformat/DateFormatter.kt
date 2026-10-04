package com.otus.mail.core.dateformat

import kotlinx.datetime.LocalDate

/** Formats display values only. Domain dates, sorting, and protocol values stay ISO. */
class DateFormatter(
    val preference: DateFormatPreference = DateFormatPreference.DeviceLocale,
    val localeIdentifier: String = currentDateLocaleIdentifier(),
) {
    private val localized =
        if (preference == DateFormatPreference.DeviceLocale) {
            createLocalizedDateFormatter(localeIdentifier)
        } else {
            null
        }

    fun format(date: LocalDate): String {
        val day = date.day.toString().padStart(TWO_DIGITS, '0')
        val month =
            (date.month.ordinal + 1)
                .toString()
                .padStart(TWO_DIGITS, '0')
        val year = date.year.toString().padStart(FOUR_DIGITS, '0')
        return when (preference) {
            DateFormatPreference.DeviceLocale -> requireNotNull(localized).format(date)
            DateFormatPreference.DayMonthYearDots -> "$day.$month.$year"
            DateFormatPreference.DayMonthYearSlashes -> "$day/$month/$year"
            DateFormatPreference.MonthDayYearDots -> "$month.$day.$year"
            DateFormatPreference.MonthDayYearSlashes -> "$month/$day/$year"
            DateFormatPreference.YearMonthDayDashes -> date.toString()
            DateFormatPreference.YearMonthDaySlashes -> "$year/$month/$day"
        }
    }

    fun formatIsoDate(date: String): String = format(LocalDate.parse(date))

    fun formatRange(
        start: LocalDate,
        end: LocalDate,
        separator: String = " → ",
    ): String = format(start) + separator + format(end)
}

internal fun interface LocalizedDateFormatter {
    fun format(date: LocalDate): String
}

expect fun currentDateLocaleIdentifier(): String

internal expect fun createLocalizedDateFormatter(localeIdentifier: String): LocalizedDateFormatter

private const val TWO_DIGITS = 2
private const val FOUR_DIGITS = 4
