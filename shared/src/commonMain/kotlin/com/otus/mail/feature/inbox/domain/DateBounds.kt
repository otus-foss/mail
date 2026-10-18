package com.otus.mail.feature.inbox.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The arrival times a [DateFilter] keeps: at or after [after] and before [before]; null is open. */
data class DateBounds(
    val after: Instant? = null,
    val before: Instant? = null,
)

/**
 * The times this filter keeps at [now] in [zone]. Today starts at local midnight, the last 7 and 30
 * days count back from [now], and a range runs from the start of its first day to the start of the
 * day after its last, each day's start taken in [zone], so daylight saving changes move it with
 * the clock.
 */
fun DateFilter.bounds(
    now: Instant,
    zone: TimeZone,
): DateBounds =
    when (this) {
        DateFilter.Any -> DateBounds()
        DateFilter.Today -> DateBounds(after = now.toLocalDateTime(zone).date.atStartOfDayIn(zone))
        DateFilter.Last7 -> DateBounds(after = now - LAST_WEEK.days)
        DateFilter.Last30 -> DateBounds(after = now - LAST_MONTH.days)
        is DateFilter.Range -> DateBounds(from.atStartOfDayIn(zone), to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone))
    }

private const val LAST_WEEK = 7
private const val LAST_MONTH = 30
