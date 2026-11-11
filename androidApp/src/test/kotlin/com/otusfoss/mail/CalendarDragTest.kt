package com.otusfoss.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarDragTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useIsoDateDisplay() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
    }

    @Test
    fun draggingAcrossWeeksUpdatesWhileHeldAndSupportsReverseDirection() {
        openCalendar()
        compose.onNodeWithContentDescription("Previous month").performClick()
        dragRange(3, 12)
        dragRange(19, 8)
        compose.onNodeWithText("Done").performClick()
        val month = LocalDate.now().minusMonths(1).withDayOfMonth(1)
        compose
            .appliedDateRange(month.withDayOfMonth(8).toString(), month.withDayOfMonth(19).toString())
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun narrowCalendarAllowsVerticalDragAcrossWeeksWithoutScrollingSheet() {
        openCalendar()
        compose.onNodeWithContentDescription("Previous month").performClick()
        dragRange(4, 18)
    }

    @Test
    fun monthYearPickerSelectsDateAndNavigatesToLeapYear() {
        openCalendar()
        compose.onNodeWithText("Today").performClick()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Year choices").performScrollToNode(hasText("2020"))
        compose.onNodeWithText("2020").performClick()
        compose.onNodeWithContentDescription("Month choices").performScrollToNode(hasText("February"))
        compose.onNodeWithText("February").performClick()
        compose.onNodeWithContentDescription("Day choices").performScrollToNode(hasText("1"))
        compose.onNode(hasText("1") and hasAnyAncestor(hasContentDescription("Day choices"))).performClick()
        compose.onNodeWithText("Go").performClick()
        compose.onNode(hasText("February 2020") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("2020-02-01 → Choose an end date").assertIsDisplayed()
        compose.onNodeWithContentDescription("2020-02-29").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp-mdpi")
    fun cancellingMonthPickerLeavesCalendarUnchangedOnSmallScreen() {
        openCalendar()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Year choices").performScrollToNode(hasText("2010"))
        compose.onNodeWithText("2010").performClick()
        compose.onNodeWithContentDescription("Close date chooser").assertIsDisplayed().performClick()
        compose.onNode(hasText(heading) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun yearChoicesDescendAndNavigationStopsAtCurrentMonth() {
        openCalendar()
        val current = LocalDate.now()
        val heading = "${current.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${current.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        val newest = compose.onNodeWithText(current.year.toString()).fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithText((current.year - 1).toString()).fetchSemanticsNode().boundsInRoot
        assertTrue("Older years appear below the current year", older.top > newest.top)
        compose.onNodeWithText((current.year + 1).toString()).assertDoesNotExist()
        compose.onNodeWithText("Go").performClick()
        compose.onNode(hasText(heading) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    @Test
    fun yearChoicesAndPreviousMonthStopAt1970() {
        openCalendar()
        val current = LocalDate.now()
        val heading = "${current.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${current.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Year choices").performScrollToNode(hasText("1970"))
        compose.onNodeWithText("1970").performClick()
        compose.onNodeWithText("1969").assertDoesNotExist()
        compose.onNodeWithContentDescription("Month choices").performScrollToNode(hasText("January"))
        compose.onNodeWithText("January").performClick()
        compose.onNodeWithText("Go").performClick()
        compose.onNode(hasText("January 1970") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").assertIsEnabled()
    }

    @Test
    fun fixedMonthControlOpensPickerAndInlineMonthMarkerHasNoSeparateAction() {
        openCalendar()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").assertIsDisplayed()
        val marker =
            today.month.name
                .take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        compose.onNodeWithText(marker, useUnmergedTree = true).assertHasNoClickAction().assertIsDisplayed()
        compose.onNodeWithContentDescription("Calendar weekdays").assertIsDisplayed()
        compose.onNode(hasText(heading) and hasClickAction()).performTouchInput { click(center) }
        compose.onNodeWithText("Choose date").assertIsDisplayed()
    }

    @Test
    fun lastThirtyDaysIncludesTodayAndPreviousTwentyNineDays() {
        openCalendar()
        compose.onNodeWithText("Last 30 days").performClick()
        val today = LocalDate.now()
        val start = today.minusDays(29)
        compose.onNodeWithText(relativeIsoRange(start.toString(), today.toString())).assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose
            .appliedDateRange(start.toString(), today.toString())
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Custom…").performClick()
        compose.onNodeWithText(relativeIsoRange(start.toString(), today.toString())).assertIsDisplayed()
    }

    @Test
    fun quickRangeHighlightTracksTheDraftAndManualSelection() {
        openCalendar()
        compose
            .onNodeWithText("Today")
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithText("Last 7 days").performClick().assertIsSelected()
        compose.onNodeWithText("Today").assertIsNotSelected()
        compose.onNodeWithText("Last 30 days").performClick().assertIsSelected()
        compose.onNodeWithText("Last 7 days").assertIsNotSelected()
        val today = LocalDate.now().toString()
        compose.onNodeWithContentDescription(today).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Last 30 days").assertIsNotSelected()
        compose.onNodeWithText("Today").assertIsNotSelected()
        compose.onNodeWithContentDescription(today).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Today").assertIsSelected()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Today").assertIsNotSelected()
    }

    @Test
    fun cancelledDragRestoresThePreviousRange() {
        openCalendar()
        compose.onNodeWithText("Today").performClick()
        val first = compose.onNodeWithContentDescription(LocalDate.now().minusDays(1).toString())
        first.performScrollTo()
        first.performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(80f, 0f))
            cancel()
        }
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
    }

    private fun openCalendar() {
        compose.openDateRange()
    }

    private fun dragRange(
        from: Int,
        to: Int,
    ) {
        val month = LocalDate.now().minusMonths(1).withDayOfMonth(1)
        val start = month.withDayOfMonth(from).toString()
        val end = month.withDayOfMonth(to).toString()
        val first = compose.onNodeWithContentDescription(start)
        first.performScrollTo()
        val calendar = compose.onNodeWithContentDescription("Calendar months")
        val viewport = calendar.fetchSemanticsNode().boundsInRoot.topLeft
        val origin = first.fetchSemanticsNode().boundsInRoot.center - viewport
        val destination =
            compose
                .onNodeWithContentDescription(end)
                .fetchSemanticsNode()
                .boundsInRoot.center - viewport
        compose.mainClock.autoAdvance = false
        calendar.performTouchInput { down(origin) }
        compose.mainClock.advanceTimeBy(600)
        calendar.performTouchInput {
            repeat(8) { step ->
                val fraction = (step + 1) / 8f
                moveTo(origin + (destination - origin) * fraction, delayMillis = 24)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(relativeIsoRange(minOf(start, end), maxOf(start, end))).assertIsDisplayed()
        compose.onNodeWithContentDescription(start).assertIsSelected()
        compose.onNodeWithContentDescription(end).assertIsSelected()
        compose.onNodeWithText("Done").assertIsEnabled()
        calendar.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(relativeIsoRange(minOf(start, end), maxOf(start, end))).assertIsDisplayed()
    }
}
