package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.InboxPill
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.InboxPills
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxHeaderActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tappingPromptFocusesSearchRequestsKeyboardAndRevealsSearchSettings() {
        var keyboardRequested = false
        val keyboard =
            object : SoftwareKeyboardController {
                override fun show() {
                    keyboardRequested = true
                }

                override fun hide() = Unit
            }
        compose.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    OtusTheme { InboxScreen(emptyList()) }
                }
            }
        }
        compose.openSearchResults()
        compose.onNodeWithContentDescription("Search your mailbox…").assertIsFocused()
        compose.onNodeWithText("People").assertIsDisplayed()
        compose.onNodeWithText("Date").assertIsDisplayed()
        compose.onNodeWithText("All accounts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inboxes").assertDoesNotExist()
        compose.runOnIdle { assertTrue("Opening search requests the software keyboard", keyboardRequested) }
    }

    @Test
    fun defaultFilterButtonOpensFiltersWithoutStartingSearch() {
        compose.onNodeWithContentDescription("Open filters").assertIsDisplayed().performClick()
        compose.filterTab("Unread").performClick()
        compose.onNodeWithText("Show conversations").performClick()
        compose.onNodeWithText("Search Inbox").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search your mailbox…").assertDoesNotExist()
        compose.onNodeWithText("Unread").assertIsOn()
        compose.onNodeWithContentDescription("Open filters, 1 active").assertIsDisplayed()
    }

    @Test
    fun withoutPillsTheFilterButtonMovesIntoTheHeaderAndTheListMovesUp() {
        fun show(pills: List<InboxPill>) =
            compose.runOnUiThread {
                compose.activity.setContent {
                    OtusTheme { InboxScreen(emptyList(), context = InboxContext(pills = InboxPills(pills))) }
                }
            }

        fun bounds(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot
        show(InboxPill.defaults(readLater = false))
        val prompt = bounds(compose.onNodeWithText("Search Inbox"))
        assertTrue("With pills, the button ends their row", bounds(filters()).top >= prompt.bottom)
        val listWithPills = bounds(compose.onNodeWithText("Your inbox is empty")).top
        show(emptyList())
        val button = bounds(filters())
        assertTrue("Without pills, it sits beside the prompt", abs(button.center.y - prompt.center.y) < 2f)
        assertTrue(bounds(compose.onNodeWithText("Your inbox is empty")).top < listWithPills)
        // Search still lists its filters, and the button stays available.
        compose.openSearchResults()
        compose.onNodeWithText("People").assertIsDisplayed()
        filters().assertIsDisplayed()
    }

    private fun filters() = compose.onNodeWithContentDescription("Open filters", substring = true)

    @Test
    fun navigationChevronIsAvailableAndSearchBackReturnsToPrompt() {
        compose.onNodeWithContentDescription("Open navigation").assertIsDisplayed().assertIsEnabled()
        compose.openSearchResults()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.onNodeWithText("Search Inbox").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open navigation").assertIsEnabled()
    }
}
