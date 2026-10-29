package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class RelativeDatePickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openCalendar() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("Date").performClick()
    }

    @Test
    fun todayShortcutUsesRelativeSummaryAndSingleDatePill() {
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        val today = compose.onNodeWithText("Today")
        today.assertIsOn().performScrollTo().performClick()
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
    }

    @Test
    fun yesterdayStartAndCompletedRangeUseRelativeLabels() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        compose.onNodeWithContentDescription(yesterday).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Yesterday → Choose an end date").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription(LocalDate.now().toString()).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Yesterday → Today").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        val range = compose.onNodeWithText("Yesterday–Today")
        range.assertIsOn().performScrollTo().performClick()
        compose.onNodeWithText("Yesterday → Today").assertIsDisplayed()
    }

    @Test
    fun yesterdaySingleDateAndWeekPresetUseRelativeLabels() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        compose.onNodeWithContentDescription(yesterday).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription(yesterday).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Yesterday → Yesterday").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        val yesterdayChip = compose.onNodeWithText("Yesterday")
        yesterdayChip.assertIsOn().performScrollTo().performClick()
        compose.onNodeWithText("Last 7 days").performClick()
        val start = LocalDate.now().minusDays(6)
        compose.onNodeWithText("$start → Today").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        val week = compose.onNodeWithText("This week")
        week.assertIsOn().performScrollTo().assertIsDisplayed()
    }
}
