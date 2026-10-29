package com.otus.mail

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Picks the last matching option, such as one in a sheet over the page. */
internal fun ComposeTestRule.pickLastText(text: String): SemanticsNodeInteraction {
    val option = onAllNodesWithText(text).onLast()
    return option.performScrollTo().performClick()
}
