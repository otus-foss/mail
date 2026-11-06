package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.dateformat.formatRelative
import com.otus.mail.app.App
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.storage.inMemoryChoice
import kotlinx.datetime.LocalDate

/** Gesture tests select ISO explicitly so their assertions do not depend on the host locale. */
fun MainActivity.showIsoDateApp() {
    setContent {
        App(remember { DateFormatSettingsState(inMemoryChoice(DateFormatPreference.YearMonthDayDashes.storageValue)) })
    }
}

/** Gesture assertions use display labels while interactions continue to address ISO dates. */
fun relativeIsoDate(date: String): String =
    DateFormatter(DateFormatPreference.YearMonthDayDashes).formatRelative(LocalDate.parse(date))

fun relativeIsoRange(
    start: String,
    end: String,
    separator: String = " → ",
): String =
    when {
        start == end && separator == "–" -> relativeIsoDate(start)
        else -> relativeIsoDate(start) + separator + relativeIsoDate(end)
    }
