package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.otus.designsystem.components.MonthYearPicker
import com.otus.designsystem.theme.OtusTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class DatePickerBoundsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate(2026, 10, 4)
    private var confirmed: LocalDate? = null

    @Test
    fun futureInitialDateIsClampedAndFutureOptionsAreAbsent() {
        showPicker(2026, 12, 31)
        choice("Month", "October").assertIsSelected()
        choice("Day", "4").assertIsSelected()
        compose.onNodeWithText("November").assertDoesNotExist()
        compose.onNodeWithText("2027").assertDoesNotExist()
        compose.onNodeWithText("5").assertDoesNotExist()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(today, confirmed) }
    }

    @Test
    fun switchingFromOlderYearClampsFutureMonthAndDayToToday() {
        showPicker(2025, 12, 31)
        choice("Month", "December").assertIsSelected()
        choice("Day", "31").assertIsSelected()
        choose("Year", "2026")
        choice("Month", "October").assertIsSelected()
        choice("Day", "4").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(today, confirmed) }
    }

    @Test
    fun earlierMonthsRetainAllDaysAndCanStillBeSelected() {
        showPicker(2026, 10, 4)
        choose("Month", "September")
        choose("Day", "30")
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(LocalDate(2026, 9, 30), confirmed) }
    }

    private fun showPicker(
        year: Int,
        month: Int,
        day: Int,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    MonthYearPicker(year, month, today, {}, { selectedYear, selectedMonth, selectedDay ->
                        confirmed = LocalDate(selectedYear, selectedMonth, selectedDay)
                    }, initialDay = day)
                }
            }
        }
    }

    private fun choose(
        column: String,
        value: String,
    ) {
        compose.onNodeWithContentDescription("$column choices").performScrollToNode(hasText(value))
        choice(column, value).performClick()
    }

    private fun choice(
        column: String,
        value: String,
    ) = compose.onNode(hasText(value) and hasAnyAncestor(hasContentDescription("$column choices")))
}
