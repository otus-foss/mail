package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertEquals
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
class CalendarCrossMonthTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useIsoDateDisplay() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
    }

    @Test
    fun heldEdgeDragCrossesYearAndKeepsItsAnchorAfterMonthRecycling() {
        openMonth("December", "2020")
        holdAtEdge("2020-12-20", bottom = true)
        compose.mainClock.advanceTimeBy(3000)
        val summary = rangeSummary("2020-12-20 →")
        val end = LocalDate.parse(summary.substringAfter(" → "))
        assertTrue("Holding near the edge must scroll into the next year", end.year > 2020)
        // Frozen rendering can lag the auto-scroll callback by a frame. Check the live release target.
        val releaseDate = bottomDateAtSunday()
        compose.onNodeWithContentDescription("Calendar months").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        val completed = "2020-12-20 → $releaseDate"
        assertEquals(completed, rangeSummary("2020-12-20 →"))
        compose.onNodeWithText(completed).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(completed, rangeSummary("2020-12-20 →"))
    }

    @Test
    fun reverseEdgeDragCrossesYearAndCancellationRestoresDraft() {
        openMonth("January", "2021")
        compose.onNodeWithContentDescription("2021-01-02").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("2021-01-02").performClick()
        compose.mainClock.advanceTimeBy(400)
        holdAtEdge("2021-01-02", bottom = false)
        compose.mainClock.advanceTimeBy(2500)
        val summary = rangeSummary(" → 2021-01-02")
        assertTrue(LocalDate.parse(summary.substringBefore(" → ")).year < 2021)
        compose.onNodeWithContentDescription("Calendar months").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("2021-01-02 → 2021-01-02").assertIsDisplayed()
    }

    private fun holdAtEdge(
        date: String,
        bottom: Boolean,
    ) {
        val day = compose.onNodeWithContentDescription(date).performScrollTo()
        val calendar = compose.onNodeWithContentDescription("Calendar months")
        val position = day.fetchSemanticsNode().boundsInRoot.center - calendar.fetchSemanticsNode().boundsInRoot.topLeft
        compose.mainClock.autoAdvance = false
        calendar.performTouchInput { down(position) }
        compose.mainClock.advanceTimeBy(600)
        calendar.performTouchInput {
            moveTo(Offset(position.x, if (bottom) height - 2f else 2f))
        }
    }

    private fun rangeSummary(part: String): String {
        val node = compose.onNode(hasText(part, substring = true)).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].single().text
    }

    private fun bottomDateAtSunday(): String {
        val viewport = compose.onNodeWithContentDescription("Calendar months").fetchSemanticsNode().boundsInRoot
        val position = Offset(viewport.right - viewport.width / 14f, viewport.bottom - 2f)
        return compose
            .onAllNodes(hasAnyAncestor(hasContentDescription("Calendar months")) and hasClickAction())
            .fetchSemanticsNodes()
            .single { it.boundsInRoot.contains(position) }
            .config[SemanticsProperties.ContentDescription]
            .single()
    }

    private fun openMonth(
        month: String,
        year: String,
    ) {
        compose.onNodeWithContentDescription("Search mailbox").performClick()
        compose.onNodeWithText("Date").performClick()
        val today = LocalDate.now()
        val heading = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        compose.onNode(hasText(heading) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Year choices").performScrollToNode(hasText(year))
        compose.onNodeWithText(year).performClick()
        compose.onNodeWithContentDescription("Month choices").performScrollToNode(hasText(month))
        compose.onNodeWithText(month).performClick()
        compose.onNodeWithText("Go").performClick()
        compose.onNodeWithText("Clear").performClick()
    }
}
