package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.TimeRange
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/** The arrival time, through `currentdate` (RFC 5260), which is the delivery time when Sieve runs. */
internal fun RuleCondition.When.whenTest(zone: String): String {
    val date = "currentdate :zone ${sieveString(zone)}"
    val tests =
        buildList {
            if (days.isNotEmpty()) {
                // Sieve counts weekdays from Sunday, 0, to Saturday, 6.
                val numbers = days.map { it.sieveWeekday }.sorted().map { it.toString() }
                add("$date :is \"weekday\" ${sieveList(numbers)}")
            }
            if (range != null) add(rangeTest(date, range, outside))
        }
    // Every day at any time.
    return if (tests.isEmpty()) "true" else tests.singleOrNull() ?: allOf(tests)
}

private fun rangeTest(
    date: String,
    range: TimeRange,
    outside: Boolean,
): String {
    val from = "$date :value \"ge\" \"time\" ${sieveString(range.start.sieveTime)}"
    val until = "$date :value \"lt\" \"time\" ${sieveString(range.end.sieveTime)}"
    val inside = if (range.start <= range.end) allOf(listOf(from, until)) else anyOf(listOf(from, until))
    return if (outside) "not $inside" else inside
}

private val DayOfWeek.sieveWeekday: Int
    get() = isoDayNumber % DAYS_PER_WEEK

private const val DAYS_PER_WEEK = 7

private val LocalTime.sieveTime: String
    get() = listOf(hour, minute, second).joinToString(":") { it.toString().padStart(2, '0') }
