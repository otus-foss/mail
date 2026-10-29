package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.otus.designsystem.shared.dates.components.SpecificDatePicker
import com.otus.designsystem.theme.OtusTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpecificDatePickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var confirmed: LocalDate? = null

    @Test
    fun requiredModeWaitsForAnExplicitDayAndRejectsEmptyConfirmation() {
        showPicker()
        choice("Day", "Pick").assertIsSelected()
        compose.onNodeWithText("Any").assertDoesNotExist()
        compose.onNodeWithText("Go").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertNull(confirmed) }
        choose("Month", "February")
        choose("Year", "2021")
        choice("Day", "Pick").assertIsSelected()
        compose.onNodeWithText("Go").assertIsNotEnabled()
        screenshot("specific-date-required-empty")
        choose("Day", "28")
        compose.onNodeWithText("Go").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(LocalDate(2021, 2, 28), confirmed) }
    }

    @Test
    fun clearingAnExplicitDayDisablesRequiredConfirmationAgain() {
        showPicker(initialDay = 31)
        compose.onNodeWithText("Go").assertIsEnabled()
        choose("Day", "Pick")
        compose.onNodeWithText("Go").assertIsNotEnabled()
        choose("Day", "12")
        compose.onNodeWithText("Go").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(LocalDate(2020, 1, 12), confirmed) }
    }

    @Test
    fun callerBoundsLimitBothEndsAndClampTheInitialDate() {
        showPicker(
            initialYear = 1990,
            initialDay = 1,
            minDate = LocalDate(2020, 5, 15),
            maxDate = LocalDate(2021, 8, 8),
        )
        choice("Year", "2020").assertIsSelected()
        choice("Month", "May").assertIsSelected()
        choice("Day", "15").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(LocalDate(2020, 5, 15), confirmed) }
        choose("Year", "2021")
        choose("Month", "August")
        choice("Day", "8").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(LocalDate(2021, 8, 8), confirmed) }
    }

    @Test
    fun futureDatesAreAllowedWhenTheCallerIncludesThem() {
        showPicker(maxDate = LocalDate(2030, 12, 31))
        choose("Year", "2030")
        choose("Month", "December")
        choose("Day", "31")
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(LocalDate(2030, 12, 31), confirmed) }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun requiredModeKeepsItsChoicesAndDisabledActionVisibleWithLargeText() {
        showPicker(fontScale = 2f)
        choice("Day", "Pick").assertIsDisplayed()
        compose.onNodeWithText("Go").assertIsDisplayed().assertIsNotEnabled()
        screenshot("specific-date-required-large-text")
        choose("Day", "10")
        compose
            .onNodeWithText("Go")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(LocalDate(2020, 1, 10), confirmed) }
    }

    private fun showPicker(
        initialYear: Int = 2020,
        initialDay: Int? = null,
        minDate: LocalDate = LocalDate(1970, 1, 1),
        maxDate: LocalDate = LocalDate(2026, 10, 8),
        fontScale: Float = 1f,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    OtusTheme {
                        SpecificDatePicker(
                            YearMonth(initialYear, 1),
                            minDate..maxDate,
                            {},
                            { confirmed = it },
                            initialDay = initialDay,
                        )
                    }
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

    private fun screenshot(name: String) = compose.dialogBitmap().savePng("build/reports/pickers/$name.png")
}
