package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.MailHeader
import com.otus.mail.feature.inbox.domain.MailLocation
import com.otus.mail.feature.inbox.domain.MailMessage
import com.otus.mail.feature.inbox.domain.MailSender
import com.otus.mail.feature.inbox.domain.MessageFolder
import com.otus.mail.feature.inbox.domain.ReceivedAt
import com.otus.mail.feature.inbox.presentation.components.FoldersSheet
import com.otus.mail.feature.inbox.presentation.components.MessageDetailsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Read view's Folders and View details sheets, with demo folders and headers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadSheetsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var applied: Pair<Set<String>, Set<String>>? = null
    private var dismissed = false

    private val message =
        MailMessage(
            "m1",
            MailSender("Ada Lovelace", "ada@lovelace.example"),
            "Invoice October",
            "",
            ReceivedAt("2026-10-18", "09:14"),
            MailLocation("Clients", "Alex", mailboxId = "cl", mailboxIds = setOf("cl", "fi")),
        )
    private val folders =
        listOf(
            MessageFolder("in", listOf("Inbox"), role = "inbox"),
            MessageFolder("sp", listOf("Junk"), role = "junk"),
            MessageFolder("cl", listOf("Clients"), color = "Blue"),
            MessageFolder("fi", listOf("Finance", "Invoices"), color = "Yellow"),
            MessageFolder("ft", listOf("Finance", "Taxes"), color = "Yellow"),
            MessageFolder("fr", listOf("Freelancing"), color = "Pink"),
            MessageFolder("fri", listOf("Freelancing", "Invoices"), color = "Pink"),
            MessageFolder("ar", listOf("Archive 2025"), color = "Green"),
        )

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                OtusTheme { Box(Modifier.fillMaxSize().background(OtusColors.bgRegular)) { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun showFolders() =
        show {
            FoldersSheet(
                message,
                mapOf(message.emailId to message.location.mailboxIds),
                folders,
                onApply = { added, removed -> applied = added to removed },
                onNewFolder = {},
                onDismiss = { dismissed = true },
            )
        }

    /**
     * The folder row named [name], as the Folders sheet's tree shows it, with [parent] under the
     * name when its parent folder is not listed.
     */
    private fun folder(
        name: String,
        parent: String? = null,
    ) = compose.onNode(
        listOfNotNull(hasText(name), parent?.let { hasText(it, substring = true) }, hasClickAction())
            .reduce { a, b -> a and b },
    )

    /** Checks that the row says whether the mail is in its folder. */
    private fun SemanticsNodeInteraction.assertTicked(ticked: Boolean) =
        assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                if (ticked) "In this folder" else "Not in this folder",
            ),
        )

    @Test
    fun theFoldersTheMailIsInAreTickedAndDoneSendsOnlyTheChanges() {
        showFolders()
        folder("Clients").assertTicked(true)
        folder("Taxes").assertTicked(false)
        compose.onNodeWithText("Drafts").assertDoesNotExist()
        compose.screenshot("2026-10-19-feat-read-view-folders-sheet")
        folder("Taxes").performClick()
        folder("Clients").performClick()
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { dismissed }
        assertEquals(setOf("ft") to setOf("cl"), applied)
    }

    @Test
    fun doneWithoutChangesSendsNothingAndAnEmptyChoiceCannotBeSaved() {
        showFolders()
        folder("Clients").performClick()
        folder("Invoices", "Finance").performClick()
        compose.onNode(hasText("Done") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Each mail needs a folder").assertIsDisplayed()
        folder("Invoices", "Finance").performClick()
        folder("Clients").performClick()
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        compose.waitUntil(TIMEOUT) { dismissed }
        assertNull(applied)
    }

    @Test
    fun detailsListEveryHeader() {
        show {
            MessageDetailsSheet(
                message,
                loadHeaders = {
                    listOf(
                        MailHeader("From", "Ada Lovelace <ada@lovelace.example>"),
                        MailHeader("To", "alex@example.org"),
                        MailHeader("Date", "Sat, 18 Oct 2026 09:14:00 +0200"),
                        MailHeader("Authentication-Results", "mx.example.org;\r\n dkim=pass header.d=lovelace.example"),
                    )
                },
                onDismiss = {},
            )
        }
        compose.onNodeWithText("Authentication-Results").assertIsDisplayed()
        compose.onNodeWithText("mx.example.org; dkim=pass header.d=lovelace.example").assertIsDisplayed()
        compose.screenshot("2026-10-19-feat-read-view-details-sheet")
    }

    @Test
    fun detailsSayWhenTheHeadersCouldNotLoad() {
        show { MessageDetailsSheet(message, loadHeaders = { null }, onDismiss = {}) }
        compose.onNodeWithText("Couldn't load the headers. Check your connection.").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
