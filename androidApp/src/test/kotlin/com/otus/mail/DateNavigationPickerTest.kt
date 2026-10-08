package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
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
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.components.MonthNavigation
import com.otus.designsystem.components.MonthNavigationLabel
import com.otus.designsystem.components.MonthYearPicker
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class DateNavigationPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun daySelectionUsesRangeBehaviorAndCancelDiscardsChanges() {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    InboxScreen(
                        emptyList(),
                        dateFormatter = DateFormatter(DateFormatPreference.YearMonthDayDashes),
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("Date").performClick()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNodeWithText(heading).performClick()
        choose("Year", "2020")
        choose("Month", "January")
        choose("Day", "5")
        compose.onNodeWithText("Go").performClick()
        val start = LocalDate.of(2020, 1, 5)
        compose.onNodeWithText("${relativeIsoDate(start.toString())} → Choose an end date").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("January 2020").performClick()
        choose("Day", "2")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("${relativeIsoDate(start.toString())} → Choose an end date").assertIsDisplayed()
        compose.onNodeWithText("January 2020").performClick()
        choose("Day", "2")
        compose.onNodeWithText("Go").performClick()
        val end = LocalDate.of(2020, 1, 2)
        compose.onNodeWithText(relativeIsoRange(end.toString(), start.toString())).assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText(relativeIsoRange(end.toString(), start.toString(), "–")).assertIsDisplayed()
    }

    @Test
    fun changingMonthAndYearClampsDayIncludingLeapYears() {
        var confirmed: List<Int>? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    MonthYearPicker(2020, 1, kotlinx.datetime.LocalDate(2026, 12, 31), {}, { year, month, day ->
                        confirmed = listOf(year, month, day)
                    }, initialDay = 31)
                }
            }
        }
        choose("Month", "February")
        day("29").assertIsSelected()
        choose("Year", "2021")
        day("28").assertIsSelected()
        choose("Month", "March")
        day("28").assertIsSelected()
        compose.onNodeWithText("Go").performClick()
        compose.runOnIdle { assertEquals(listOf(2021, 3, 28), confirmed) }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun smallScreenAndLargeTextKeepDateChoicesAndActionsReachable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { MonthYearPicker(2020, 1, kotlinx.datetime.LocalDate(2026, 12, 31), {}, { _, _, _ -> }) }
                }
            }
        }
        choose("Day", "31")
        choose("Month", "February")
        choose("Year", "2021")
        compose.onNodeWithContentDescription("Day choices").performScrollToNode(hasText("28"))
        day("28").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Go").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun monthNamesKeepYearAndArrowPositionsStable() {
        val month = mutableStateOf("May")
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme { MonthNavigation(MonthNavigationLabel(month.value, "2026", 0), {}, {}, {}) }
            }
        }
        val yearBefore =
            compose
                .onNode(
                    hasText("2026") and hasAnyAncestor(hasText("2026")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val previousBefore = compose.onNodeWithContentDescription("Previous month").fetchSemanticsNode().boundsInRoot
        val nextBefore = compose.onNodeWithContentDescription("Next month").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { month.value = "September" }
        val yearAfter =
            compose
                .onNode(
                    hasText("2026") and hasAnyAncestor(hasText("2026")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(yearBefore.center.x, yearAfter.center.x, 1f)
        assertEquals(
            previousBefore,
            compose.onNodeWithContentDescription("Previous month").fetchSemanticsNode().boundsInRoot,
        )
        assertEquals(nextBefore, compose.onNodeWithContentDescription("Next month").fetchSemanticsNode().boundsInRoot)
    }

    private fun choose(
        column: String,
        value: String,
    ) {
        compose.onNodeWithContentDescription("$column choices").performScrollToNode(hasText(value))
        compose.onNode(hasText(value) and hasAnyAncestor(hasContentDescription("$column choices"))).performClick()
    }

    private fun day(value: String) =
        compose.onNode(hasText(value) and hasAnyAncestor(hasContentDescription("Day choices")))
}
