package com.otus.mail.core.dateformat

import android.text.format.DateFormat
import kotlinx.datetime.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

actual fun currentDateLocaleIdentifier(): String = Locale.getDefault().toLanguageTag()

internal actual fun createLocalizedDateFormatter(localeIdentifier: String): LocalizedDateFormatter {
    val locale = Locale.forLanguageTag(localeIdentifier.replace('_', '-'))
    val utc = TimeZone.getTimeZone("UTC")
    val formatter =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "yyyyMMdd"), locale).apply {
            calendar = GregorianCalendar(utc, locale).apply { gregorianChange = Date(Long.MIN_VALUE) }
            timeZone = utc
        }
    return LocalizedDateFormatter { date: LocalDate ->
        // Date-only values are anchored in UTC, never in the device's time zone.
        synchronized(formatter) { formatter.format(Date(date.toEpochDays() * MILLIS_PER_DAY)) }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
