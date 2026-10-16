package com.otus.mail

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.otus.mail.app.App
import com.otus.mail.feature.inbox.presentation.MailBehaviorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Settings › Folders: the account picker, folding, shared mailboxes, and dragging folders. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderListUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openFolders(withBob: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setContent {
                App(
                    services = server.services(withBob = withBob),
                    behaviorState = MailBehaviorState(),
                )
            }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Folders").performClick()
        compose.waitUntil(TIMEOUT) { count("Amazon") > 0 }
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().size

    private fun folderRow(name: String) = compose.onNode(hasText(name) and hasClickAction() and !isSelectable())

    @Test
    fun thePickerSwitchesBetweenAllAccountsAndOne() {
        openFolders(withBob = true)
        compose.onNodeWithText("All accounts").assertExists()
        compose.onNodeWithText("2 accounts · 4 folders").assertExists()
        assertEquals("Each account lists its folders", 2, count("Amazon"))
        screenshot("all-accounts")
        compose.onNodeWithText("Ada").performClick()
        compose.waitUntil(TIMEOUT) { count("Amazon") == 1 }

        compose.onNodeWithText("All accounts").performClick()
        compose.onNode(hasText("Bob") and isSelectable() and !hasText("folders", substring = true)).performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("2 folders").assertExists()
        assertEquals(1, count("Amazon"))
        assertEquals("One account has no group rows", 0, count("Ada"))
    }

    @Test
    fun theParentIconFoldsItsSubfolders() {
        openFolders()
        compose.onNodeWithContentDescription("Collapse Receipts").performClick()
        compose.waitUntil(TIMEOUT) { count("Amazon") == 0 }
        compose.onNodeWithContentDescription("Expand Receipts").performClick()
        compose.waitUntil(TIMEOUT) { count("Amazon") == 1 }
        assertTrue("Folding opens nothing", server.calls.none { "Mailbox/set" in it })
    }

    @Test
    fun aSharedMailboxShowsWhoSharesItOnce() {
        server.sharedFolders += """{"id": "r", "name": "Design reviews", "myRights": {"mayAddItems": false}}"""
        openFolders(withBob = true)
        compose.waitUntil(TIMEOUT) { count("Design reviews") > 0 }
        screenshot("shared")
        assertEquals(1, count("Design reviews"))
        assertEquals(1, count("Shared by grace@example.org · can read"))
        assertEquals("The login's own Receipts keeps its id", 2, count("Receipts"))
    }

    @Test
    fun droppingAFolderInTheGapAboveAnotherSavesTheNewOrder() {
        server.folders += """{"id": "t", "name": "Travel"}"""
        openFolders()
        dragRow("Travel", to = "Receipts", share = EDGE)
        val update = lastMailboxUpdate()
        assertTrue(update, "\"update\":{\"t\":{\"sortOrder\":10},\"r\":{\"sortOrder\":20}}" in update)
    }

    @Test
    fun droppingAFolderInTheGapAboveASubfolderNestsItThere() {
        server.folders += """{"id": "t", "name": "Travel"}"""
        openFolders()
        dragRow("Travel", to = "Amazon", share = EDGE)
        val update = lastMailboxUpdate()
        assertTrue(update, Regex(""""t":\{[^}]*"parentId":"r"""").containsMatchIn(update))
    }

    @Test
    fun droppingAFolderOntoAnotherMakesItTheFirstSubfolder() {
        server.folders += """{"id": "t", "name": "Travel"}"""
        openFolders()
        dragRow("Travel", to = "Receipts", share = MIDDLE)
        val update = lastMailboxUpdate()
        assertTrue(update, Regex(""""t":\{[^}]*"parentId":"r"""").containsMatchIn(update))
        assertTrue(update, Regex(""""t":\{[^}]*"sortOrder":10""").containsMatchIn(update))
        assertTrue(update, "\"z\":{\"sortOrder\":20}" in update)
    }

    @Test
    fun droppingAFolderIntoItsOwnSubfolderSavesNothing() {
        openFolders()
        dragRow("Receipts", to = "Amazon", share = MIDDLE)
        assertTrue(server.calls.none { "Mailbox/set" in it && "\"update\"" in it })
    }

    /** Holds [name]'s row, then drags its center to [share] of [to]'s row height and lifts. */
    private fun dragRow(
        name: String,
        to: String,
        share: Float,
    ) {
        val target = folderRow(to).fetchSemanticsNode().boundsInRoot
        val row = folderRow(name)
        val start = row.fetchSemanticsNode().boundsInRoot
        val distance = target.top + target.height * share - start.center.y
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS_MILLIS)
        row.performTouchInput {
            val from = center
            (1..STEPS).forEach { step -> moveTo(from + Offset(0f, distance * step / STEPS)) }
            up()
        }
        compose.waitForIdle()
    }

    private fun lastMailboxUpdate(): String {
        compose.waitUntil(TIMEOUT) { server.calls.any { "Mailbox/set" in it && "\"update\"" in it } }
        return server.calls.last { "Mailbox/set" in it && "\"update\"" in it }
    }

    /** Saves the screen to build/reports/folders for review. */
    private fun screenshot(name: String) {
        val bitmap =
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        val directory = File("build/reports/folders").apply { mkdirs() }
        File(
            directory,
            "folder-list-$name.png",
        ).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val LONG_PRESS_MILLIS = 600L
        const val STEPS = 4

        /** A share of a row's height in its upper quarter, which drops into the gap above it. */
        const val EDGE = 0.1f

        /** The middle of a row, which drops into it. */
        const val MIDDLE = 0.5f
    }
}
