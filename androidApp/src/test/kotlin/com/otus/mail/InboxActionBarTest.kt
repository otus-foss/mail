package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
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

    private fun start(behavior: MailBehavior = MailBehavior(markReadOnOpen = false)) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(), behaviorState = MailBehaviorState(initial = behavior))
            }
        }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Welcome")).fetchSemanticsNodes().isNotEmpty() }
        // Folder actions appear once folders and rules have loaded.
        compose.waitUntil(TIMEOUT) { server.calls.any { "SieveScript/get" in it } }
        compose.waitForIdle()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    @Test
    fun theListShowsItsDefaultButtonsAndSearchOpensTheSearchField() {
        start()
        listOf("Search", "Filters", "Mark all read", "New mail").forEach { button(it).assertIsEnabled() }
        button("Search").performClick()
        compose.onNodeWithContentDescription("Search your mailbox…").assertIsFocused()
        button("New mail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        button("New mail").assertIsEnabled()
    }

    @Test
    fun filtersOpensTheFilterPanel() {
        start()
        button("Filters").performClick()
        compose.onNodeWithContentDescription("Close filters").assertExists()
    }

    @Test
    fun markAllReadMarksTheUnreadMessagesOnTheServer() {
        start()
        button("Mark all read").performClick()
        compose.waitUntil(TIMEOUT) { server.calls.any { "Email/set" in it } }
        val read = server.calls.last { "Email/set" in it }
        assertTrue(read, "\"update\":{\"m1\":{\"keywords/\$seen\":true}}" in read)
        button("Mark all read").assertIsNotEnabled()
    }

    @Test
    fun aFixedBarIsTheSameOnEveryScreenWithInapplicableButtonsDisabled() {
        server.folders += """{"id": "x", "name": "Old mail", "role": "archive"}"""
        start(MailBehavior(markReadOnOpen = false, actionBarMode = ActionBarMode.Fixed))
        button("Search").assertIsEnabled()
        button("New mail").assertIsEnabled()
        listOf("Archive", "Delete").forEach { button(it).assertIsNotEnabled() }

        compose.onNodeWithText("Welcome").performClick()
        button("Search").assertIsNotEnabled()
        button("Archive").assertIsEnabled()
        button("New mail").assertIsEnabled()
        button("Mark all read").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
