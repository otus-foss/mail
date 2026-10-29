package com.otus.mail.feature.folders.presentation

import com.otus.mail.feature.folders.domain.RuleCondition
import com.otus.mail.feature.folders.domain.WEEKDAYS
import com.otus.mail.feature.folders.domain.WEEKEND
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * A time range is bright, with "outside" before it and the days after a dot, both dim, as in
 * "outside 9:00–18:00 · weekdays". Without a range, the days alone are bright, as in "Weekends".
 */
internal fun RuleCondition.When.whenChip(): RuleChipText {
    val range = range
    return if (range == null) {
        RuleChipText(
            "When",
            ChipSlot.Icon(RuleIcon.CalendarMonth),
            bright(daysText(days).replaceFirstChar { it.uppercase() }),
        )
    } else {
        val words = if (outside) "outside " else ""
        val dayWords = if (days.isEmpty()) "" else " · " + daysText(days)
        val time = "${timeText(range.start)}–${timeText(range.end)}"
        RuleChipText("When", ChipSlot.Icon(RuleIcon.Schedule), dim(words) + bright(time) + dim(dayWords))
    }
}

/** "weekdays", "weekends", "every day", or the days in week order, such as "Mon, Wed". */
private fun daysText(days: Set<DayOfWeek>): String =
    when (days) {
        WEEKDAYS -> {
            "weekdays"
        }

        WEEKEND -> {
            "weekends"
        }

        emptySet<DayOfWeek>(), DayOfWeek.entries.toSet() -> {
            "every day"
        }

        else -> {
            days.sorted().joinToString(", ") { day ->
                day.name.take(SHORT_DAY).lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

private const val SHORT_DAY = 3

/** Hours without a leading zero, as in "9:00". */
private fun timeText(time: LocalTime): String = "${time.hour}:${time.minute.toString().padStart(2, '0')}"
