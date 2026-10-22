package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.otus.designsystem.theme.OtusTheme
import com.otus.designsystem.theme.panelTextStyle
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.OwnAddresses
import com.otus.mail.feature.inbox.presentation.InboxContext
import com.otus.mail.feature.inbox.presentation.RowLook
import com.otus.mail.feature.inbox.presentation.components.NewFolderSheet
import com.otus.mail.feature.inbox.presentation.screens.MessageScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Read view with demo mail: pages, strips, the ⋯ menu, images, and quotes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadViewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixtures = ReadViewFixtures
    private var applied: Pair<Set<String>, Set<String>>? = null
    private var unsubscribed = 0

    private fun show(
        row: MailMessage,
        pages: List<MailMessage> = listOf(row),
        cameFrom: Set<String> = emptySet(),
        newFolder: NewFolderSheet? = null,
        showQuotes: Boolean = false,
    ) {
        val read =
            fixtures
                .read(
                    pages,
                    cameFrom,
                    { applied = it.added to it.removed },
                    { unsubscribed++ },
                    newFolder,
                ).let { if (showQuotes) with(fixtures) { it.showingQuotes() } else it }
        val context =
            InboxContext(
                loadContent = { fixtures.content(it) },
                read = read,
                rowLook = RowLook(own = OwnAddresses(mapOf("acc" to listOf(fixtures.alex.address)))),
            )
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme {
                    ProvideTextStyle(panelTextStyle()) {
                        MessageScreen(row, fixtures.actions(), null, context, cameFrom, onClose = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun strip(description: String): SemanticsNodeInteraction {
        compose.waitUntil(
            TIMEOUT,
        ) {
            compose
                .onAllNodes(
                    hasContentDescription(description, substring = true),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        return compose.onNode(hasContentDescription(description, substring = true))
    }

    private fun exists(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun openMenu() = compose.onNodeWithContentDescription("More actions").performClick()

    private fun showThread() = show(fixtures.threadRow, fixtures.thread, cameFrom = setOf("in"))

    @Test
    fun aThreadOpensOnItsNewestPageAndTheListJumpsToAnOlderOne() {
        showThread()
        strip("Grace Hopper, message 1 of 6").assertIsDisplayed()
        compose.screenshot("read-thread-newest")
        strip("Grace Hopper, message 1 of 6").performClick()
        compose.onNodeWithText("6 messages").assertIsDisplayed()
        assertTrue(exists("You"))
        compose.screenshot("read-thread-list")
        compose.onNode(hasText("Margaret Knuth", substring = true) and isSelectable()).performClick()
        strip("Margaret Knuth, message 5 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("From Margaret Knuth. Show details").assertIsDisplayed()
        compose.screenshot("read-thread-older")
    }

    @Test
    fun swipingLeftGoesBackInTime() {
        showThread()
        strip("Grace Hopper, message 1 of 6")
        compose.onNodeWithText("Let's lock it in", substring = true).performTouchInput { swipeLeft() }
        strip("Alex Doe, message 2 of 6").assertIsDisplayed()
    }

    @Test
    fun swipingTheThreadStripTurnsThePagesToo() {
        showThread()
        strip("Grace Hopper, message 1 of 6").performTouchInput { swipeLeft() }
        strip("Alex Doe, message 2 of 6").assertIsDisplayed()
        strip("Alex Doe, message 2 of 6").performTouchInput { swipeRight() }
        strip("Grace Hopper, message 1 of 6").assertIsDisplayed()
    }

    @Test
    fun theThreadStripCardStaysWhileItsContentSlides() {
        showThread()
        val strip = strip("Grace Hopper, message 1 of 6")
        val bounds = strip.getBoundsInRoot()
        strip.performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(-width / 3f / DRAG_STEPS, 0f)) }
        }
        compose.waitForIdle()
        compose.screenshot("read-thread-strip-dragging")
        assertEquals("The card stays put while its content slides", bounds, strip.getBoundsInRoot())
        strip.performTouchInput { up() }
        strip("Alex Doe, message 2 of 6").assertIsDisplayed()
    }

    @Test
    fun aScrollThatDriftsSidewaysStaysOnThePage() {
        showThread()
        // A thumb that starts slightly sideways, then scrolls down: the old pager took it as a flick.
        compose.onNodeWithText("Let's lock it in", substring = true).performTouchInput {
            val turn = Offset(-60f, -10f)
            val end = Offset(-250f, -500f)
            swipe({ t -> center + if (t < 30) turn * (t / 30f) else turn + (end - turn) * ((t - 30) / 120f) }, 150)
        }
        strip("Grace Hopper, message 1 of 6").assertIsDisplayed()
    }

    @Test
    fun manyMessagesKeepTheDotsShortAndThePositionExact() {
        val pages = List(24) { i -> fixtures.thread[i % fixtures.thread.size].copy(id = "p$i", emailId = "p$i") }
        show(fixtures.threadRow, pages)
        strip("Grace Hopper, message 1 of 24").assertIsDisplayed()
        compose.screenshot("read-thread-many")
    }

    @Test
    fun aSingleMailWithNothingToAddHasNoStrip() {
        val alone = fixtures.invoice.copy(location = fixtures.invoice.location.copy(mailboxIds = setOf("cl")))
        show(alone, cameFrom = setOf("cl"))
        compose.onNodeWithText("Hi Alex", substring = true).assertIsDisplayed()
        assertTrue(!exists("Also in"))
        assertTrue(
            compose.onAllNodes(hasContentDescription("message 1 of", substring = true)).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun aSingleMailInAnotherFolderSaysWhereAndItsSheetAppliesTicks() {
        show(fixtures.invoice, cameFrom = setOf("cl"))
        compose.onNodeWithText("Also in").assertIsDisplayed()
        assertTrue(exists("Invoices"))
        compose.screenshot("read-also-in")
        compose.onNodeWithText("Also in").performClick()
        compose.onNode(hasText("Freelancing") and hasClickAction()).performClick()
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { applied != null }
        assertEquals(setOf("fr") to emptySet<String>(), applied)
    }

    @Test
    fun theFoldersSheetStartsANewFolderAndComesBackAfterIt() {
        show(
            fixtures.invoice,
            cameFrom = setOf("cl"),
            newFolder =
                NewFolderSheet { accountId, onDismiss ->
                    Text("Naming a folder in $accountId", Modifier.clickable(onClick = onDismiss))
                },
        )
        compose.onNodeWithText("Also in").performClick()
        compose.onNodeWithContentDescription("New folder").performClick()
        compose.onNodeWithText("Naming a folder in acc").performClick()
        assertTrue(exists("Folders"))
    }

    @Test
    fun theMenuActsOnThePageThenTheSenderThenTheConversation() {
        showThread()
        strip("Grace Hopper, message 1 of 6")
        openMenu()
        listOf(
            "Reply to Grace",
            "Forward",
            "Mark as unread",
            "Grace Hopper",
            "Block sender",
            "Whole conversation",
            "Mute conversation",
        ).forEach { assertTrue(it, exists(it)) }
        assertTrue(!exists("Delete this message"))
        compose.screenshot("read-menu")
    }

    @Test
    fun aNewsletterOffersUnsubscribeAndConfirmsInPlace() {
        show(fixtures.digest)
        compose.waitUntil(TIMEOUT) { exists("Images blocked for privacy") && exists("Mailing list") }
        compose.screenshot("read-newsletter")
        compose.onNode(hasText("Unsubscribe") and hasClickAction()).performClick()
        compose.onNodeWithText("Unsubscribed").assertIsDisplayed()
        assertTrue(exists("23 older digests"))
        compose.screenshot("read-newsletter-unsubscribed")
        compose.onNode(hasText("Show") and hasClickAction()).performClick()
        compose.onNodeWithText("Images shown").assertIsDisplayed()
        compose.screenshot("read-images-shown")
        compose.onNode(hasText("Hide") and hasClickAction()).performClick()
        compose.onNodeWithText("Images blocked for privacy").assertIsDisplayed()
    }

    @Test
    fun attachmentsOpenInTheirStrip() {
        showThread()
        compose.waitUntil(TIMEOUT) { exists("3 attachments") }
        compose.onNodeWithText("3 attachments", substring = true).performClick()
        compose.onNodeWithText("Save all").assertIsDisplayed()
        assertTrue(exists("whiteboard.jpg"))
        compose.screenshot("read-attachments")
    }

    @Test
    fun theSenderPillShowsTheFullRecord() {
        showThread()
        compose.onNodeWithContentDescription("From Grace Hopper. Show details").performClick()
        compose.onNodeWithText("12 Oct 2026, 09:14").assertIsDisplayed()
        compose.onNodeWithText("Folders").assertIsDisplayed()
        compose.screenshot("read-sender-details")
    }

    @Test
    fun aReplyInAThreadLeavesOutWhatItQuotes() {
        showThread()
        strip("Grace Hopper, message 1 of 6").performClick()
        compose.onNode(hasText("Katherine Johnson", substring = true) and isSelectable()).performClick()
        strip("Katherine Johnson, message").assertIsDisplayed()
        compose.onNodeWithText("Show quoted text").assertDoesNotExist()
    }

    @Test
    fun aQuotedReplyFoldsItsQuoteWhenQuotesAreShown() {
        show(fixtures.threadRow, fixtures.thread, cameFrom = setOf("in"), showQuotes = true)
        strip("Grace Hopper, message 1 of 6").performClick()
        compose.onNode(hasText("Katherine Johnson", substring = true) and isSelectable()).performClick()
        compose.onNodeWithText("Show quoted text").assertIsDisplayed()
        compose.screenshot("read-quoted")
        compose.onNodeWithText("Show quoted text").performClick()
        compose.onNodeWithText("Hide quoted text").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val DRAG_STEPS = 8
    }
}
