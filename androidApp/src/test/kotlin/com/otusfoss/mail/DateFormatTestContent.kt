package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otusfoss.core.dateformat.DateFormatPreference
import com.otusfoss.core.dateformat.DateFormatter
import com.otusfoss.core.dateformat.formatRelative
import com.otusfoss.core.settings.presentation.DateFormatSettingsState
import com.otusfoss.core.storage.inMemoryChoice
import com.otusfoss.mail.app.App
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

/** Opens the range picker the filter panel's Date row offers under Custom…. */
internal fun ComposeTestRule.openDateRange() {
    onNodeWithContentDescription("Open filters", substring = true).performClick()
    onNodeWithContentDescription("Date, ", substring = true).performScrollTo().performClick()
    onNodeWithText("Custom…").performScrollTo().performClick()
}

/** The filter panel's Date row once [start] to [end] is applied, in the ISO format the tests choose. */
internal fun ComposeTestRule.appliedDateRange(
    start: String,
    end: String = start,
): SemanticsNodeInteraction =
    onNodeWithContentDescription("Date, ${if (start == end) start else "$start – $end"},", substring = true)
