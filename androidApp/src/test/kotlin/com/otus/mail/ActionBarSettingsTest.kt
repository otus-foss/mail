package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private fun openActionBarSettings() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance & behavior").performClick()
        compose.onNodeWithText("Per screen").performScrollTo()
        compose.onNodeWithText("Action bar").performScrollTo().performClick()
    }

    private fun button(label: String) = compose.onNode(hasContentDescription(label) and hasClickAction())

    @Test
    fun aButtonChosenForTheOpenMessageIsSavedAndShownOnItsBar() {
        server.folders += """{"id": "s", "name": "Junk", "role": "junk"}"""
        start()
        openActionBarSettings()
        // The third "Button 1" row belongs to the open message's section.
        compose.onAllNodesWithText("Button 1")[2].performScrollTo().performClick()
        compose.onNodeWithText("Spam").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Spam").performScrollTo().assertIsDisplayed()

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
    fun oneBarForEveryScreenReplacesThePerScreenSections() {
        start()
        openActionBarSettings()
        listOf("Message list", "Open message", "Selected messages", "Writing").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Every screen").assertDoesNotExist()

        compose.onNodeWithText("Layout").performScrollTo().performClick()
        compose.onNodeWithText("Same everywhere").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Every screen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open message").assertDoesNotExist()
        // Fixed mode edits the one shared bar and leaves each screen's own bar saved.
        compose.onAllNodesWithText("Button 1")[0].performScrollTo().performClick()
        compose.onNodeWithText("Reply").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()

        val saved = AndroidMailBehaviorStore(compose.activity).read()
        assertEquals(ActionBarMode.Fixed, saved.actionBarMode)
        val everywhere =
            ActionBarLayout(listOf(MailAction.Reply, MailAction.Archive, MailAction.Trash), MailAction.NewMail)
        assertEquals(everywhere, saved.barLayout(ActionBarPlace.Inbox))
        assertEquals(ActionBarPlace.Message.defaultLayout, saved.savedLayout(ActionBarPlace.Message))

        compose.onNodeWithText("Same everywhere").performScrollTo().performClick()
        compose.onNodeWithText("Per screen").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Open message").performScrollTo().assertIsDisplayed()
        assertEquals(ActionBarMode.Adaptive, AndroidMailBehaviorStore(compose.activity).read().actionBarMode)
    }

    @Test
    fun writingOffersOnlyAsManyButtonSlotsAsItHasActions() {
        start()
        openActionBarSettings()
        // Writing has Send, Attach, and Discard, so beside its main button it has two slots.
        assertEquals(4, compose.onAllNodesWithText("Button 2").fetchSemanticsNodes().size)
        assertEquals(3, compose.onAllNodesWithText("Button 3").fetchSemanticsNodes().size)
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
