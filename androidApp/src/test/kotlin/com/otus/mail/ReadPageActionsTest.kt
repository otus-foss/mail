package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.otus.designsystem.shared.feedback.components.ToastHost
import com.otus.designsystem.shared.feedback.components.ToastState
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.feature.inbox.domain.FolderChange
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MessageFlag
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.ReadContext
import com.otus.mail.feature.inbox.presentation.ReadEffects
import com.otus.mail.feature.inbox.presentation.RowLook
import com.otus.mail.feature.inbox.presentation.screens.MessageScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the Read view's page entries and strips send: one email at a time, the whole conversation's
 * folders together, and an unsubscribe only once its toast closes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class ReadPageActionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures
    private val changes = RecordedPageChanges()
    private val toasts = ToastState()
    private var applied: FolderChange? = null
    private var unsubscribed = 0

    private fun show(
        row: MailMessage,
        pages: List<MailMessage>,
    ) {
        // Work that waits for a toast runs at once, so the test sees when it was sent.
        val effects = ReadEffects(toasts, launch = { work -> runBlocking { work() } })
        val read: ReadContext =
            fixtures.read(
                pages,
                setOf("in"),
                { applied = it },
                { unsubscribed++ },
                changes = changes,
                effects = effects,
            )
        val context = InboxContext(loadContent = { fixtures.content(it) }, read = read, rowLook = RowLook(own = OWN))
        val actions = fixtures.actions(targets = listOf(MoveTarget("tr", listOf("Trash"), MailboxRole.Trash)))
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        Box(Modifier.fillMaxSize()) {
                            MessageScreen(row, actions, null, context, setOf("in"), onClose = {})
                            ToastHost(toasts, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun strip(description: String): SemanticsNodeInteraction {
        val strip = hasContentDescription(description, substring = true)
        compose.waitForNode(strip)
        return compose.onNode(strip)
    }

    /** Opens the page of [sender] from the grown thread strip. */
    private fun openPage(sender: String) {
        show(fixtures.threadRow, fixtures.thread)
        strip("message 1 of 6").performClick()
        compose.onNode(hasText(sender, substring = true) and isSelectable()).performClick()
    }

    private fun menu(entry: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText(entry).performScrollTo().performClick()
    }

    @Test
    fun marksOnAnOlderPageGoToItsOwnEmailOnly() {
        openPage("Radia Perlman")
        strip("Radia Perlman, message 3 of 6")
        menu("Mark as unread")
        menu("Flag")
        assertEquals(listOf("r" to MessageFlag.Read, "r" to MessageFlag.Flagged), changes.marks)
    }

    @Test
    fun deletingAnOlderPageTrashesOnlyItsEmailAndUndoBringsItBack() {
        openPage("Margaret Knuth")
        strip("Margaret Knuth, message 5 of 6")
        menu("Delete this message")
        assertEquals(listOf("m"), changes.trashed)
        strip("message 5 of 5")
        compose.runOnUiThread { changes.undo() }
        strip(" of 6")
    }

    @Test
    fun theFoldersSheetChangesEveryEmailOfTheConversation() {
        show(fixtures.threadRow, fixtures.thread)
        strip("also in Invoices")
        compose.onNodeWithContentDescription("From Grace Hopper. Show details").performClick()
        compose.onNode(hasText("Invoices", substring = true) and hasClickAction()).performClick()
        compose.onNode(hasText("Inbox") and isToggleable()).performClick()
        compose.onNodeWithText("Each mail needs a folder").assertIsDisplayed()
        compose.onNode(hasText("Done") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Inbox") and isToggleable()).performClick()
        compose.onNode(hasText("Clients") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { applied != null }
        val change = checkNotNull(applied)
        assertEquals(fixtures.thread.map { it.emailId }.toSet(), change.before.keys)
        assertEquals(setOf("in", "fi", "cl"), change.after.getValue("g"))
        assertEquals(setOf("in", "cl"), change.after.getValue("k"))
    }

    @Test
    fun anUnsubscribeIsSentOnlyOnceItsToastCloses() {
        compose.mainClock.autoAdvance = false
        show(fixtures.digest, listOf(fixtures.digest))
        unsubscribe()
        assertEquals(0, unsubscribed)
        compose.mainClock.advanceTimeBy(TOAST_GONE)
        compose.waitForIdle()
        assertEquals(1, unsubscribed)
    }

    @Test
    fun undoKeepsTheSubscription() {
        compose.mainClock.autoAdvance = false
        show(fixtures.digest, listOf(fixtures.digest))
        unsubscribe()
        compose.onNode(hasText("Undo") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(TOAST_GONE)
        compose.waitForIdle()
        assertEquals(0, unsubscribed)
        compose.onNode(hasText("Unsubscribe") and hasClickAction()).assertIsDisplayed()
    }

    private fun unsubscribe() {
        compose.mainClock.advanceTimeUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Unsubscribe") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Unsubscribe") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(FRAME)
        compose.onNodeWithText("Unsubscribed from Sentry weekly digest").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val FRAME = 100L
        const val TOAST_GONE = 20_000L
        val OWN = OwnAddresses(mapOf("acc" to listOf(ReadViewFixtures.alex.address)))
    }
}
