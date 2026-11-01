package com.otus.mail

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An overlay of its own takes the whole inbox page, control panel included, out of TalkBack's reach. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class InboxCoverSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val switcherButton = hasContentDescription("Showing mail from", substring = true)

    private fun reachable() = compose.onAllNodes(switcherButton).fetchSemanticsNodes().size

    @Test
    fun theAccountSwitcherHidesThePanelUnderIt() {
        compose.showControlInbox()
        assertEquals(1, reachable())
        compose.onNode(switcherButton).performClick()
        compose.waitForIdle()
        assertEquals("The panel under the switcher", 0, reachable())
    }
}
