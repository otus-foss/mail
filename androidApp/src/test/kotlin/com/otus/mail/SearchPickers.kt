package com.otus.mail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals

/** A choice to tick in the open search filter sheet; the chips behind it can show the same text. */
internal fun ComposeTestRule.pickRow(text: String): SemanticsNodeInteraction =
    onNode(hasText(text) and isToggleable() and hasAnyAncestor(isDialog()))

/** The open sheet's own search field, such as "Search people…". */
internal fun ComposeTestRule.pickerSearch(what: String): SemanticsNodeInteraction =
    onNodeWithContentDescription("Search $what…")

/** Taps the scrim above the sheet named [title], which drops what the sheet picked. */
internal fun ComposeTestRule.dismissSheet(title: String) {
    onNodeWithContentDescription("Dismiss $title").performTouchInput { click(Offset(center.x, 10f)) }
}

/** Shows the search's results in the list, as submitting the field does. */
internal fun ComposeTestRule.showSearchResults() {
    searchField().performClick().performImeAction()
    waitForIdle()
}

/** Checks that the list shows [count] rows, each of which reads as read or unread. */
internal fun ComposeTestRule.assertListed(count: Int) =
    assertEquals(
        count,
        onAllNodes(hasStateDescription("Read") or hasStateDescription("Unread")).fetchSemanticsNodes().size,
    )
