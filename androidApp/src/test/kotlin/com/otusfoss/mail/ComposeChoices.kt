package com.otusfoss.mail

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Picks the last matching option, such as one in a sheet over the page. */
internal fun ComposeTestRule.pickLastText(text: String): SemanticsNodeInteraction =
    onAllNodesWithText(text)
        .onLast()
        .performScrollTo()
        .performClick()
