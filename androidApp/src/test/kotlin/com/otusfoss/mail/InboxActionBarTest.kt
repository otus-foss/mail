package com.otusfoss.mail

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otusfoss.mail.core.messages.domain.ActionBarMode
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The message list's bar with nothing selected, and one fixed bar on every screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InboxActionBarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun start(behavior: MailBehavior = MailBehavior(read = ReadBehavior(markReadOnOpen = false))) =
        compose.showInbox(server, MailBehaviorState(initial = behavior))

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    private fun allActions(entry: String) {
        button("All actions").performClick()
        compose.onNodeWithText(entry).performScrollTo().performClick()
    }

    @Test
    fun theListShowsItsDefaultButtonsAndSearchOpensTheSearchField() {
        start()
        listOf("All actions", "Mark all read", "New mail").forEach { button(it).assertIsEnabled() }
        listOf("Search", "Filters").forEach { button(it).assertDoesNotExist() }
        allActions("Search")
        compose.searchField().assertIsFocused()
        button("New mail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        button("New mail").assertIsEnabled()
    }

    @Test
    fun filtersOpensTheFilterPanel() {
        start()
        allActions("Filters")
        // The Show button in the bar reads "Show mails" while it counts and "Show 1 mail" after.
        compose.onNodeWithContentDescription("Show ", substring = true).assertExists()
    }

    @Test
    fun markAllReadMarksTheUnreadMessagesOnTheServer() {
        start()
        button("Mark all read").performClick()
        compose.waitForCall(server, "Email/set")
        val read = server.calls.last { "Email/set" in it }
        assertTrue(read, "\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in read)
        button("Mark all read").assertIsNotEnabled()
    }

    @Test
    fun aFixedBarIsTheSameOnEveryScreenWithInapplicableButtonsDisabled() {
        server.folders += """{"id": "x", "name": "Old mail", "role": "archive"}"""
        start(
            MailBehavior(
                read = ReadBehavior(markReadOnOpen = false),
                actions = ActionBehavior(barMode = ActionBarMode.Fixed),
            ),
        )
        button("All actions").assertIsEnabled()
        button("New mail").assertIsEnabled()
        listOf("Archive", "Delete").forEach { button(it).assertIsNotEnabled() }

        compose.onNodeWithText("Welcome").performClick()
        button("Archive").assertIsEnabled()
        button("New mail").assertIsEnabled()
        button("Mark all read").assertDoesNotExist()
    }
}
