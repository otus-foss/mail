package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.shared.dates.components.MonthYearPicker
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OptionalDayPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var confirmed: Pair<YearMonth, LocalDate?>? = null

    @Test
    fun openingAndChangingMonthAndYearLeavesDayUnselected() {
        showPicker()
        choice("Day", "Any").assertIsSelected()
        screenshot("choose-date-optional-day")
        choose("Month", "February")
        choose("Year", "2021")
        choice("Day", "Any").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(YearMonth(2021, 2) to null, confirmed) }
    }

    @Test
    fun explicitlyChosenDayCanBeCleared() {
        showPicker()
        choose("Day", "31")
        choice("Day", "31").assertIsSelected()
        choose("Day", "Any")
        choice("Day", "Any").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(YearMonth(2020, 1) to null, confirmed) }
    }

    @Test
    fun swipingTheDayWheelSelectsItsCenteredDay() {
        showPicker()
        compose.onNodeWithContentDescription("Day choices").performTouchInput { swipeUp() }
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertNotEquals(null, requireNotNull(confirmed).second) }
    }

    @Test
    fun monthOnlyNavigationPreservesTheRangeAndReopeningResetsDay() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    InboxScreen(
                        emptyList(),
                        dateFormatter = DateFormatter(DateFormatPreference.YearMonthDayDashes),
                        context = inboxContext(),
                    )
                }
            }
        }
        compose.openSearchResults()
        compose.onNodeWithText("Date").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText(todayHeading()).performClick()
        choose("Year", "2020")
        choose("Month", "January")
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("January 2020").assertIsDisplayed().performClick()
        choice("Day", "Any").assertIsSelected()
        choose("Day", "5")
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("January 2020").performClick()
        choice("Day", "Any").assertIsSelected()
        compose.onNodeWithContentDescription("Close date chooser").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText(todayHeading()).performClick()
        choose("Year", "2020")
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun largeTextKeepsOptionalDayAndMainActionVisible() {
        showPicker(fontScale = 2f)
        choice("Day", "Any").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close date chooser").assertIsDisplayed()
        compose.onNodeWithText("Go").assertIsDisplayed()
        screenshot("choose-date-small-large-text")
    }

    private fun showPicker(fontScale: Float = 1f) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    OtusTheme {
                        MonthYearPicker(
                            YearMonth(2020, 1),
                            LocalDate(1970, 1, 1)..LocalDate(2026, 10, 8),
                            {},
                            { month, date -> confirmed = month to date },
                        )
                    }
                }
            }
        }
    }

    private fun todayHeading(): String {
        val today = java.time.LocalDate.now()
        return "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
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

    private fun screenshot(name: String) = compose.dialogBitmap().savePng("build/reports/pickers/$name.png")
}
