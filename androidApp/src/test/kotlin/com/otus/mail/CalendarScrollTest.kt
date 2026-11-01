package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
class CalendarScrollTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useIsoDateDisplay() {
        compose.runOnUiThread { compose.activity.showIsoDateApp() }
    }

    @Test
    fun verticalDragScrollsPartOfAMonthWithoutChangingTheRange() {
        compose.openSearchResults()
        compose.onNodeWithText("Date").performClick()
        compose.onNodeWithText("Today").performClick()
        val today = LocalDate.now()
        compose.onNodeWithContentDescription("Previous month").performClick()
        val first = compose.onNodeWithContentDescription(today.minusMonths(1).withDayOfMonth(8).toString())
        val initial = first.fetchSemanticsNode().boundsInRoot.top
        val calendar = compose.onNodeWithContentDescription("Calendar months")
        calendar.performTouchInput {
            down(Offset(center.x, height * 0.75f))
            moveBy(Offset(0f, -60f), delayMillis = 150)
            moveBy(Offset(0f, -30f), delayMillis = 150)
            // End without fling velocity so settling must retain the partial month offset.
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        val moved = first.fetchSemanticsNode().boundsInRoot.top
        assertTrue("The list must keep a partial month scroll instead of snapping", moved < initial)
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
        val previous = today.minusMonths(1)
        val heading = "${previous.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${previous.year}"
        val date = previous.withDayOfMonth(1).toString()
        calendar.performScrollToNode(hasContentDescription(date))
        val marker = compose.onNodeWithContentDescription(date)
        val offset = marker.fetchSemanticsNode().boundsInRoot.top - calendar.fetchSemanticsNode().boundsInRoot.top
        calendar.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, offset) }
        compose.onNode(hasText(heading) and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Today → Today").assertIsDisplayed()
    }
}
