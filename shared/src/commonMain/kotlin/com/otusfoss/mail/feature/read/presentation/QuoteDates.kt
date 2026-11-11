package com.otusfoss.mail.feature.read.presentation

import com.otusfoss.core.dateformat.DateFormatPreference
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.formatNamedDay
import kotlinx.datetime.LocalDate

/** [written] as "10 Oct 2026, 17:50" when it names a day, month, and year; otherwise as written. */
internal fun fullDateLabel(written: String): String =
    parseDate(written)?.let { day ->
        QUOTE_DATES.formatNamedDay(day, withYear = true) + TIME.find(written)?.let { ", ${it.value}" }.orEmpty()
    } ?: written

/** [written] as "10 Oct" when it names a day, month, and year; otherwise as written. */
internal fun dateLabel(written: String): String = parseDate(written)?.let(QUOTE_DATES::formatNamedDay) ?: written

// Quote headers are read while the message is parsed, away from the user's date format, so they
// name the day first as before. Following the format needs a QuoteHeader that keeps the date.
private val QUOTE_DATES = DateFormatter(DateFormatPreference.DayMonthYearDots)

private fun parseDate(written: String): LocalDate? =
    ISO_DATE.find(written)?.destructured?.let { (year, month, day) -> date(year, month, day) }
        ?: DOTTED_DATE.find(written)?.destructured?.let { (day, month, year) -> date(year, month, day) }
        ?: slashedDate(written)
        ?: namedMonthDate(written)

/**
 * A date such as "10/17/26", as Proton Mail writes it, read only when one number is past 12 and so
 * can only be the day; "5/6/26" stays as written, since it could be either order.
 */
private fun slashedDate(written: String): LocalDate? {
    val (first, second, year) = SLASHED_DATE.find(written)?.destructured ?: return null
    val fullYear = if (year.length == 2) "20$year" else year
    return when {
        first.toInt() > MONTHS_IN_YEAR -> date(fullYear, second, first)
        second.toInt() > MONTHS_IN_YEAR -> date(fullYear, first, second)
        else -> null
    }
}

/** A date with the month's name, such as "Thu, Oct 10, 2026" or "10 Oct 2026". */
private fun namedMonthDate(written: String): LocalDate? {
    val month = MONTH.find(written)?.let { found -> MONTHS.indexOfFirst { found.groupValues[1].lowercase() in it } + 1 }
    val year = YEAR.find(written)?.value
    val day = DAY.findAll(written).map { it.value }.firstOrNull { it != year }
    return if (month == null || year == null || day == null) null else date(year, month.toString(), day)
}

private fun date(
    year: String,
    month: String,
    day: String,
): LocalDate? = runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

/** A time of day as mail apps write it, such as "17:50" or "5:50 PM". */
internal val TIME = Regex("""\d{1,2}:\d{2}(?::\d{2})?(?:\s?[AaPp]\.?[Mm]\.?)?""")
private val ISO_DATE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
private val DOTTED_DATE = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b""")
private val SLASHED_DATE = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4}|\d{2})\b""")
private const val MONTHS_IN_YEAR = 12
private val MONTHS =
    listOf("jan", "feb", "mar|mär", "apr", "may|mai", "jun", "jul", "aug", "sep", "oct|okt", "nov", "dec|dez")
        .map { it.split('|') }
private val MONTH =
    Regex("""\b(${MONTHS.flatten().joinToString("|")})[a-zä]*\.?(?![a-z])""", RegexOption.IGNORE_CASE)
private val YEAR = Regex("""\b\d{4}\b""")
private val DAY = Regex("""(?<![:\d])\b\d{1,2}\b(?![:\d])""")
