package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.otus.designsystem.theme.Motion
import com.otus.designsystem.theme.MotionSpeed
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.domain.MailAction
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Swiping inbox rows, and choosing what each swipe does on the appearance page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun start(behavior: MailBehavior = MailBehavior()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(services = server.services(), behaviorState = MailBehaviorState(initial = behavior))
            }
        }
    }

    /** Rows offer their swipes to accessibility services too, so this waits until folders have loaded. */
    private fun waitForSwipe(label: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(offers(label)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun offers(label: String) =
        SemanticsMatcher("offers $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private fun updates(): List<String> = server.calls.filter { "Email/set" in it }

    @Test
    fun swipingLeftDeletesTheMessageIntoTrashByDefault() {
        server.folders += """{"id": "t", "name": "Deleted", "role": "trash"}"""
        start()
        waitForSwipe("Delete")
        compose.onNodeWithText("Welcome").performTouchInput { swipeLeft() }
        compose.waitUntil(TIMEOUT) { updates().isNotEmpty() }
        val delete = updates().single()
        assertTrue(delete, "\"update\":{\"m1\":{\"mailboxIds/a\":null,\"mailboxIds/t\":true}}" in delete)
    }

    @Test
    fun swipingRightTogglesTheReadMarkAndKeepsTheRow() {
        start()
        waitForSwipe("Mark read")
        compose.onNodeWithText("Welcome").performTouchInput { swipeRight() }
        compose.waitUntil(TIMEOUT) { updates().isNotEmpty() }
        assertTrue(updates().single(), "\"keywords/\$seen\":true" in updates().single())
        waitForSwipe("Mark unread")
        compose.onNodeWithText("Welcome").assertIsDisplayed()
    }

    @Test
    fun aTapOnARowReturningFromASwipeDoesNotRunItsActionAgain() {
        start()
        waitForSwipe("Mark read")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Welcome").performTouchInput { swipeRight() }
        // The row slides out and then back, each at the relaxed speed; this tap lands on its way back.
        compose.mainClock.advanceTimeBy(Motion.millis(MotionSpeed.Relaxed) * 4L / 3)
        compose.onNodeWithText("Welcome").performTouchInput { click() }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(TIMEOUT) { updates().isNotEmpty() }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(offers("Mark read")).fetchSemanticsNodes().isEmpty())
        assertTrue(updates().single(), "\"keywords/\$seen\":true" in updates().single())
    }

    @Test
    fun aSwipeSetToReadLaterSavesTheMessageAndLabelsItsRow() {
        server.folders += """{"id": "l", "name": "Read later"}"""
        start(MailBehavior(endToStart = MailAction.ReadLater, readLater = true))
        waitForSwipe("Read later")
        compose.onNodeWithText("Welcome").performTouchInput { swipeLeft() }
        compose.waitUntil(TIMEOUT) { updates().isNotEmpty() }
        val save = updates().single()
        assertTrue(save, "\"update\":{\"m1\":{\"mailboxIds/l\":true}}" in save)
        // The row stays, names its new folder, and now offers to leave it.
        compose.onNode(hasText("Welcome") and hasText("Read later")).assertIsDisplayed()
        waitForSwipe("Remove from Read later")
        // The existing folder is used, not created again.
        assertTrue(server.calls.none { "Mailbox/set" in it })
    }

    @Test
    fun aSwipeSetToNothingOrWithoutAFolderLeavesTheRowStill() {
        // No folder has the archive role, and the other side is turned off.
        start(MailBehavior(startToEnd = null, endToStart = MailAction.Archive))
        waitFor("Welcome")
        compose.waitUntil(TIMEOUT) { server.calls.any { "Mailbox/get" in it } }
        compose.waitForIdle()
        compose.onNodeWithText("Welcome").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Welcome").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(offers("Archive")).fetchSemanticsNodes().isEmpty())
        assertTrue(updates().isEmpty())
    }

    @Test
    fun theMailBehaviorPageSavesEachChange() {
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Mail behavior").performClick()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Swipe left").performClick()
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithText("Mark as read when opened").performScrollTo().performClick()

        val saved = AndroidMailBehaviorStore(compose.activity).read()
        assertEquals(MailAction.Archive, saved.endToStart)
        assertEquals(MailAction.Read, saved.startToEnd)
        assertFalse(saved.markReadOnOpen)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
