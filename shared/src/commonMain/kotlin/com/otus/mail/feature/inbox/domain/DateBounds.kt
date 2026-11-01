package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The arrival times a [DateFilter] keeps: at or after [after] and before [before]; null is open. */
data class DateBounds(
    val after: Instant? = null,
    val before: Instant? = null,
)

/**
 * The times this filter keeps at [now] in [zone]. Today starts at local midnight, This year at
 * local midnight on 1 January, the last 7 and 30 days at local midnight six or 29 days before
 * today, and a range runs from the start of its first day to the start of the day after its last,
 * each day's start taken in [zone], so daylight saving changes move it with the clock. Every bound
 * is a day's start, so a list's filter, which names its saved pages, stays the same all day.
 */
fun DateFilter.bounds(
    now: Instant,
    zone: TimeZone,
): DateBounds =
    when (this) {
        DateFilter.Any -> DateBounds()
        DateFilter.Today -> DateBounds(after = now.toLocalDateTime(zone).date.atStartOfDayIn(zone))
        DateFilter.Last7 -> DateBounds(after = now.daysBack(LAST_WEEK - 1, zone))
        DateFilter.Last30 -> DateBounds(after = now.daysBack(LAST_MONTH - 1, zone))
        DateFilter.ThisYear -> DateBounds(after = LocalDate(now.toLocalDateTime(zone).year, 1, 1).atStartOfDayIn(zone))
        is DateFilter.Range -> DateBounds(from.atStartOfDayIn(zone), to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone))
    }

/** The start of the local day [days] days before the one [this] falls on in [zone]. */
private fun Instant.daysBack(
    days: Int,
    zone: TimeZone,
): Instant =
    toLocalDateTime(zone)
        .date
        .minus(DatePeriod(days = days))
        .atStartOfDayIn(zone)

private const val LAST_WEEK = 7
private const val LAST_MONTH = 30
