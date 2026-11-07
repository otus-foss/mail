package com.otus.mail.feature.folders.presentation

import com.otus.dateformat.clockTime
import com.otus.dateformat.shortWeekday
import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.WEEKDAYS
import com.otus.mail.feature.folders.domain.WEEKEND
import kotlinx.datetime.DayOfWeek

/**
 * A time range is bright, with "outside" before it and the days after a dot, both dim, as in
 * "outside 9:00–18:00 · weekdays". Without a range, the days alone are bright, as in "Weekends".
 */
internal fun RuleCondition.When.whenChip(): RuleChipText {
    val timeRange = range
    return if (timeRange == null) {
        RuleChipText(
            "When",
            ChipSlot.Icon(RuleIcon.CalendarMonth),
            bright(daysText(days).replaceFirstChar { it.uppercase() }),
        )
    } else {
        val words = if (outside) "outside " else ""
        val dayWords = if (days.isEmpty()) "" else " · " + daysText(days)
        val time = "${clockTime(timeRange.start)}–${clockTime(timeRange.end)}"
        RuleChipText("When", ChipSlot.Icon(RuleIcon.Schedule), dim(words) + bright(time) + dim(dayWords))
    }
}

/** "weekdays", "weekends", "every day", or the days in week order, such as "Mon, Wed". */
private fun daysText(days: Set<DayOfWeek>): String =
    when (days) {
        WEEKDAYS -> "weekdays"
        WEEKEND -> "weekends"
        emptySet<DayOfWeek>(), DayOfWeek.entries.toSet() -> "every day"
        else -> days.sorted().joinToString(", ") { shortWeekday(it) }
    }
