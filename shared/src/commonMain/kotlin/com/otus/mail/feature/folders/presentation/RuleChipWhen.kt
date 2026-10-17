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
                day.name.take(SHORT_DAY).lowercase().replaceFirstChar {
                    it.uppercase()
                }
            }
        }
    }

private const val SHORT_DAY = 3

/** Hours without a leading zero, as in "9:00". */
private fun timeText(time: LocalTime): String = "${time.hour}:${time.minute.toString().padStart(2, '0')}"

/** Whole units in binary steps, as in "10 MB"; one decimal when needed, as in "1.5 MB". */
internal fun sizeText(bytes: Long): String {
    val (amount, unit) =
        SIZE_UNITS
            .lastOrNull { (step, _) -> bytes >= step }
            ?.let { (step, unit) -> bytes.toDouble() / step to unit }
            ?: (bytes.toDouble() to "bytes")
    val rounded = kotlin.math.round(amount * DECIMAL) / DECIMAL
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$number $unit"
}

private const val KILOBYTE = 1024L
private const val DECIMAL = 10.0
private val SIZE_UNITS = listOf(KILOBYTE to "KB", KILOBYTE * KILOBYTE to "MB", KILOBYTE * KILOBYTE * KILOBYTE to "GB")
