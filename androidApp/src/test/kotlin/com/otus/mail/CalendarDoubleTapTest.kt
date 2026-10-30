package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
class CalendarDoubleTapTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useIsoDateDisplay() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
    }

    @Test
    fun doubleTapReplacesExistingRangeWithDateThroughToday() {
        openCalendar()
        compose.onNodeWithText("Last 30 days").performClick()
        val today = LocalDate.now()
        // On the 7th and the 30th, the 1st starts the "This week" or "This month" preset instead of a range.
        val date = today.withDayOfMonth(if (today.dayOfMonth in setOf(7, 30)) 2 else 1)
        compose.onNodeWithContentDescription(date.toString()).performScrollTo().performTouchInput { doubleClick() }
        compose.onNodeWithText(relativeIsoRange(date.toString(), today.toString())).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsEnabled().performClick()
        compose
            .onNodeWithText(relativeIsoRange(date.toString(), today.toString(), "–"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun doubleTapTodaySelectsOneDayAndHighlightsTodayShortcut() {
        openCalendar()
        val today = LocalDate.now()
        compose.onNodeWithContentDescription(today.toString()).performScrollTo().performTouchInput { doubleClick() }
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsSelected()
    }

    @Test
    fun futureDatesAreAbsentAndTodayRemainsSelectable() {
        openCalendar()
        val today = LocalDate.now()
        compose.onNodeWithContentDescription(today.toString()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(today.plusDays(1).toString()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    private fun openCalendar() {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("Date").performClick()
    }
}
