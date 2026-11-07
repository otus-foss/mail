package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.dateformat.DateFormatPreference
import com.otus.mail.app.App
import com.otus.settings.presentation.DateFormatSettingsState
import com.otus.storage.inMemoryChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateFormatPreferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun changingThePreferenceUpdatesAnOpenDatePickerAndItsAppliedRange() {
        val settings = DateFormatSettingsState(inMemoryChoice(DateFormatPreference.DayMonthYearDots.storageValue))
        compose.runOnUiThread { compose.activity.setContent { App(settings) } }
        openDatePicker()
        compose.onNodeWithText("Last 7 days").performClick()
        val start = LocalDate.now().minusDays(6)
        val first = start.format(DateTimeFormatter.ofPattern("dd.MM.uuuu"))
        compose.onNodeWithText("$first → Today").assertIsDisplayed()
        compose.runOnIdle { settings.select(DateFormatPreference.MonthDayYearSlashes) }
        val next = start.format(DateTimeFormatter.ofPattern("MM/dd/uuuu"))
        compose.onNodeWithText("$next → Today").assertIsDisplayed()
        compose.onNodeWithText("Last 7 days").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Date, $next – ", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithText("$next → Today").assertIsDisplayed()
        compose.onNodeWithText("Last 7 days").assertIsSelected()
    }

    @Test
    fun productionEntryPointRestoresTheSavedChoiceOnActivityRecreation() {
        compose.activity.dateFormatChoice().write(DateFormatPreference.DayMonthYearSlashes.storageValue)
        compose.activityRule.scenario.recreate()
        openDatePicker()
        compose.onNodeWithText("Last 7 days").performClick()
        val start = LocalDate.now().minusDays(6).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
        compose.onNodeWithText("$start → Today").assertIsDisplayed()
    }

    private fun openDatePicker() {
        compose.openDateRange()
    }
}
