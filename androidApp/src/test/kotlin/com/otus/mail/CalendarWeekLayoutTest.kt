package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.otus.dateformat.DateFormatPreference
import com.otus.dateformat.DateFormatter
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarWeekLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useIsoDateDisplay() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
    }

    @Test
    fun largeTextKeepsMarkedAndUnmarkedDaysAlignedAndSelectable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    OtusTheme { InboxScreen(emptyList(), DateFormatter(DateFormatPreference.YearMonthDayDashes)) }
                }
            }
        }
        openDecember2020()
        compose.onNodeWithContentDescription("Calendar months").performScrollToNode(hasContentDescription("2021-01-01"))
        val marked = compose.onNodeWithContentDescription("2021-01-01")
        val previous = compose.onNodeWithContentDescription("2020-12-31").fetchSemanticsNode().boundsInRoot
        val current = marked.fetchSemanticsNode().boundsInRoot
        assertEquals(previous.top, current.top, 1f)
        assertEquals(previous.height, current.height, 1f)
        val marker = compose.onNodeWithText("Jan", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(current.contains(marker.topLeft) && current.contains(marker.center))
        assertDayCentered("2021-01-01", "1")
        assertDayCentered("2020-12-31", "31")
        marked.performClick()
        compose.mainClock.advanceTimeBy(400)
        marked.assertIsSelected()
    }

    @Test
    fun yearBoundaryUsesOneFullRowAndSupportsSelectionAcrossItsMonthMarker() {
        openDecember2020()
        val calendar = compose.onNodeWithContentDescription("Calendar months")
        calendar.performScrollToNode(hasContentDescription("2020-12-28"))
        val dates = (0..6).map { LocalDate.of(2020, 12, 28).plusDays(it.toLong()).toString() }
        val cells =
            dates.map { date ->
                compose.onAllNodesWithContentDescription(date).assertCountEquals(1)
                compose
                    .onNodeWithContentDescription(date)
                    .assertIsDisplayed()
                    .fetchSemanticsNode()
                    .boundsInRoot
            }
        cells.zipWithNext().forEach { (left, right) ->
            assertEquals(left.top, right.top, 1f)
            assertEquals(left.right, right.left, 1f)
        }
        val marker = compose.onNodeWithText("Jan", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithContentDescription("2021-01-01").fetchSemanticsNode().boundsInRoot
        assertTrue("The month marker belongs inside day 1", first.contains(marker.center))
        assertDayCentered("2021-01-01", "1")
        assertDayCentered("2020-12-31", "31")
        val viewport = calendar.fetchSemanticsNode().boundsInRoot.topLeft
        compose.mainClock.autoAdvance = false
        calendar.performTouchInput { down(cells[2].center - viewport) }
        compose.mainClock.advanceTimeBy(600)
        calendar.performTouchInput { moveTo(cells[6].center - viewport) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("2020-12-30 → 2021-01-03").assertIsDisplayed()
        dates.drop(2).forEach { compose.onNodeWithContentDescription(it).assertIsSelected() }
        saveScreenshot()
        calendar.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("2020-12-30 → 2021-01-03").assertIsDisplayed()
    }

    private fun assertDayCentered(
        date: String,
        label: String,
    ) {
        val cell = compose.onNodeWithContentDescription(date).fetchSemanticsNode().boundsInRoot
        val text =
            compose
                .onNode(hasText(label) and hasAnyAncestor(hasContentDescription(date)), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals("Day numbers remain vertically centered", cell.center.y, text.center.y, 1f)
    }

    private fun openDecember2020() {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("Date").performClick()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Year choices").performScrollToNode(hasText("2020"))
        compose.onNodeWithText("2020").performClick()
        compose.onNodeWithContentDescription("Month choices").performScrollToNode(hasText("December"))
        compose.onNodeWithText("December").performClick()
        compose.onNodeWithText("Go").performClick()
    }

    private fun saveScreenshot() {
        compose
            .runOnIdle {
                val dialog = ShadowDialog.getShownDialogs().last { it.isShowing }
                requireNotNull(dialog.window).decorView.drawnBitmap()
            }.savePng("build/reports/pickers/calendar-continuous-week.png")
    }
}
