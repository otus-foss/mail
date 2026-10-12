package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Choosing the action bar's buttons on the appearance page, and the open message using them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionBarSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun start() {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(
                    services = server.services(),
                    behaviorState = MailBehaviorState(AndroidMailBehaviorStore(compose.activity)),
                )
            }
        }
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Welcome")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openAppearance() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance & behavior").performClick()
    }

    private fun openActionBarSettings() {
        openAppearance()
        compose.onNode(hasText("Action bar") and hasClickAction()).performScrollTo().performClick()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    /** The topmost match, such as a button in the editor's preview over the page's cards. */
    private fun top(label: String) = compose.onAllNodes(hasContentDescription(label) and hasClickAction()).onLast()

    private fun pick(text: String) =
        compose
            .onAllNodesWithText(text)
            .onLast()
            .performScrollTo()
            .performClick()

    private fun done() = compose.onAllNodesWithText("Done").onLast().performClick()

    @Test
    fun aButtonAddedToTheOpenMessageIsSavedAndShownOnItsBar() {
        server.folders += """{"id": "s", "name": "Junk", "role": "junk"}"""
        start()
        openActionBarSettings()
        compose.onNodeWithText("Open message").performScrollTo().performClick()
        top("Archive").performClick()
        pick("Spam")
        done()
        done()

        val saved = AndroidMailBehaviorStore(compose.activity).read()
        val expected =
            ActionBarLayout(listOf(MailAction.Spam, MailAction.Trash, MailAction.Move), MailAction.Reply)
        assertEquals(expected, saved.barLayout(ActionBarPlace.Message))
        assertEquals(ActionBarPlace.Selection.defaultLayout, saved.barLayout(ActionBarPlace.Selection))

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isToggleable()).performClick()
        compose.onNodeWithText("Welcome").performClick()
        button("Spam").assertIsEnabled()
        button("Archive").assertDoesNotExist()
    }

    @Test
    fun aSecondaryPickedAsTheMainButtonTradesPlacesWithIt() {
        start()
        openActionBarSettings()
        compose.onNodeWithText("Open message").performScrollTo().performClick()
        top("Reply").performClick()
        compose.onNodeWithText("Swaps with Button 1").assertIsDisplayed()
        pick("Archive")
        done()
        done()

        val expected = ActionBarLayout(listOf(MailAction.Reply, MailAction.Trash, MailAction.Move), MailAction.Archive)
        compose.runOnIdle {
            assertEquals(
                expected,
                AndroidMailBehaviorStore(compose.activity).read().savedLayout(ActionBarPlace.Message),
            )
        }
    }

    @Test
    fun aMinimizedBarShowsOnlyTheMainButton() {
        start()
        openAppearance()
        compose
            .onNode(hasText("Minimize") and isToggleable())
            .performScrollTo()
            .performClick()
            .assertIsOn()
        assertEquals(true, AndroidMailBehaviorStore(compose.activity).read().minimizedBar)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNode(hasText("Inbox") and isToggleable()).performClick()
        compose.onNodeWithText("Welcome").performClick()
        compose.onNode(hasContentDescription("Reply", substring = true) and hasClickAction()).assertIsDisplayed()
        button("Archive").assertDoesNotExist()
    }

    @Test
    fun oneBarForEveryScreenReplacesThePerScreenCards() {
        start()
        openActionBarSettings()
        listOf("Message list", "Open message", "Selected messages", "Writing").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Every screen").assertDoesNotExist()

        compose.onNodeWithText("Layout").performScrollTo().performClick()
        compose.onNodeWithText("Same everywhere").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Open message").assertDoesNotExist()
        // Fixed mode edits the one shared bar and leaves each screen's own bar saved.
        compose.onNodeWithText("Every screen").performScrollTo().performClick()
        top("Search").performClick()
        pick("Remove from bar")
        top("Add button").performClick()
        pick("Reply")
        done()
        done()

        val saved = AndroidMailBehaviorStore(compose.activity).read()
        assertEquals(ActionBarMode.Fixed, saved.actionBarMode)
        val everywhere =
            ActionBarLayout(listOf(MailAction.Archive, MailAction.Trash, MailAction.Reply), MailAction.NewMail)
        assertEquals(everywhere, saved.barLayout(ActionBarPlace.Inbox))
        assertEquals(ActionBarPlace.Message.defaultLayout, saved.savedLayout(ActionBarPlace.Message))

        compose.onNodeWithText("Same everywhere").performScrollTo().performClick()
        compose.onNodeWithText("Per screen").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Open message").performScrollTo().assertIsDisplayed()
        assertEquals(ActionBarMode.Adaptive, AndroidMailBehaviorStore(compose.activity).read().actionBarMode)
    }

    @Test
    fun theOpenMessageBarCopiesOnlyToScreensThatCanUseIt() {
        start()
        openActionBarSettings()
        compose.onNodeWithText("Open message").performScrollTo().performClick()
        button("Copy to other screens").performClick()
        compose.onNodeWithText("Message list and Writing can't use these buttons.").performScrollTo()
        compose
            .onNode(hasText("Selected messages") and isToggleable())
            .performScrollTo()
            .performClick()
            .assertIsOn()
        compose.onNodeWithText("Done").performClick()
        // Done copies once the sheet has slid away.
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("Copy to")).fetchSemanticsNodes().isEmpty() }

        val saved = AndroidMailBehaviorStore(compose.activity).read()
        val selection = ActionBarLayout(listOf(MailAction.Archive, MailAction.Trash), MailAction.Move)
        assertEquals(selection, saved.savedLayout(ActionBarPlace.Selection))
        assertEquals(ActionBarPlace.Everywhere.defaultLayout, saved.savedLayout(ActionBarPlace.Everywhere))
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
