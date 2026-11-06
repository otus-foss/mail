package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.material.ProvideTextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.typography.panelTextStyle
import com.otus.mail.core.mailbox.MailboxRole
import com.otus.mail.core.messages.domain.OwnAddresses
import com.otus.mail.core.messages.presentation.MoveTarget
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.RowLook
import com.otus.mail.feature.inbox.presentation.screens.MessageScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Read view's panel pulled down: the quick view with Move to, then every detail. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadQuickViewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures
    private val targets =
        listOf(
            MoveTarget("in", listOf("Inbox"), MailboxRole.Inbox),
            MoveTarget("ar", listOf("Archive"), MailboxRole.Archive),
            MoveTarget("tr", listOf("Trash"), MailboxRole.Trash),
            MoveTarget("pr", listOf("Projects"), own = true),
        )

    private fun showThread() {
        val context =
            InboxContext(
                loadContent = { fixtures.content(it) },
                read = fixtures.read(fixtures.thread, setOf("in")),
                rowLook = RowLook(own = OwnAddresses(mapOf("acc" to listOf(fixtures.alex.address)))),
            )
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        MessageScreen(
                            fixtures.threadRow,
                            fixtures.actions(targets = targets),
                            null,
                            context,
                            setOf("in"),
                            onClose = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pull(distance: Dp) {
        compose.onRoot().performTouchInput {
            val start = Offset(centerX, PANEL_GRIP.toPx())
            swipe(start, start + Offset(0f, distance.toPx() + viewConfiguration.touchSlop), PULL_MILLIS)
        }
        compose.waitForIdle()
    }

    private fun exists(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun pullingDownShowsTheQuickViewThenEveryDetail() {
        showThread()
        compose.waitUntil(TIMEOUT) { exists("Let's lock it in") }
        compose.screenshot("read-quick-view-regular")
        pull(QUICK_PULL)
        compose.screenshot("read-quick-view-quick")
        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithContentDescription("All folders").assertIsDisplayed()
        pull(DETAILS_PULL)
        compose.onNodeWithText("Show original").assertIsDisplayed()
        compose.screenshot("read-quick-view-details")
    }

    @Test
    fun allFoldersOpensTheMoveSheet() {
        showThread()
        compose.waitUntil(TIMEOUT) { exists("Let's lock it in") }
        pull(QUICK_PULL)
        compose.onNodeWithContentDescription("All folders").performClick()
        compose.waitUntil(TIMEOUT) { exists("Projects") && !exists("Show original") }
        compose.onNodeWithText("This message has no headers", substring = true).assertDoesNotExist()
        compose.screenshot("read-quick-view-move-sheet")
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val PULL_MILLIS = 600L
        val PANEL_GRIP = 60.dp
        val QUICK_PULL = 260.dp
        val DETAILS_PULL = 500.dp
    }
}
