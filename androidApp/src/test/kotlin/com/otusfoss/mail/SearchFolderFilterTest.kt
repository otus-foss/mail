package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.otusfoss.core.designsystem.theme.OtusTheme
import com.otusfoss.mail.core.mailbox.MailboxRole
import com.otusfoss.mail.core.messages.domain.FolderRef
import com.otusfoss.mail.core.messages.domain.MailLocation
import com.otusfoss.mail.core.messages.domain.MailMessage
import com.otusfoss.mail.core.messages.domain.MailScope
import com.otusfoss.mail.core.messages.domain.MailSender
import com.otusfoss.mail.core.messages.domain.ReceivedAt
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.ListKind
import com.otusfoss.mail.feature.inbox.presentation.InboxFilterState
import com.otusfoss.mail.feature.inbox.presentation.MailboxSearch
import com.otusfoss.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Default search scope and Filters > Folders behavior on Inbox and specific folder pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchFolderFilterTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun inboxSearchLoadsAllNonBinMailAllowsIncludingSpamAndKeepsNonInboxResults() {
        val loaded = mutableListOf<MailScope?>()
        val devMessage =
            MailMessage(
                "dev-1",
                MailSender("Alex", "alex@example.org"),
                "Build green",
                "CI passed",
                ReceivedAt("2026-10-18", "09:00"),
                MailLocation("Dev", "Ada", accountId = "ada", mailboxId = "dev"),
            )
        var filterState: InboxFilterState? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                val filters = remember { InboxFilterState(scope = scope, counter = { _, _ -> 3 }) }
                filterState = filters
                OtusTheme {
                    InboxScreen(
                        listOf(devMessage),
                        context =
                            inboxContext {
                                this.filters = filters
                                filterOptions = FilterPanelFixtures.options
                                mailboxSearch = MailboxSearch(load = { loaded += it })
                            },
                    )
                }
            }
        }
        compose.onNodeWithText("Search mail").assertIsDisplayed()
        compose.onNodeWithText("Build green").assertIsDisplayed()
        compose.openSearch("Search mail")
        compose.runOnIdle { assertEquals(MailScope.AllMail, loaded.last()) }
        compose.onNodeWithContentDescription("All filters").performClick()
        compose.onNodeWithContentDescription("Folders, Any, opens page").performScrollTo().performClick()
        compose.onNodeWithText("Spam").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Folders, Any + Spam, opens page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show 3 mails").performClick()
        compose.runOnIdle {
            assertEquals(setOf(MailboxRole.Junk), filterState?.applied?.includedBins)
        }
        compose.onNodeWithText("+ Spam").assertIsDisplayed()
    }

    @Test
    fun folderSearchStaysOnItsFolderWithoutAPanePillUntilChanged() {
        val loaded = mutableListOf<MailScope?>()
        val dev = FolderRef("ada", "dev", includeSubfolders = false)
        val travel = FolderRef("ada", "travel", includeSubfolders = false)
        var filterState: InboxFilterState? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                val filters = remember { InboxFilterState(scope = scope, counter = { _, _ -> 5 }) }
                filterState = filters
                OtusTheme {
                    InboxScreen(
                        emptyList(),
                        context =
                            inboxContext {
                                title = "Dev"
                                emptyTitle = "Dev is empty"
                                this.filters = filters
                                filterOptions =
                                    FilterPanelFixtures.options.copy(folders = false, kind = ListKind.Folder)
                                mailboxSearch = MailboxSearch(load = { loaded += it })
                                searchFolders = setOf(dev)
                            },
                    )
                }
            }
        }
        compose.openSearch("Search Dev")
        // The search starts on the page's own scope, with no load of all mail on the way.
        compose.runOnIdle {
            assertEquals(listOf<MailScope?>(null), loaded)
            assertEquals(InboxFilters(), filterState?.applied)
        }
        // Default folder selection is not counted as an active filter in the pane.
        compose.onNodeWithContentDescription("All filters").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Reset filters").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Folders, Dev, opens page").performScrollTo().performClick()
        // Adding Travel differs from the default, so the feed widens and the pane pill appears.
        compose.onNodeWithText("Travel").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Folders, Dev and 1 more, opens page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show 5 mails").performClick()
        compose.runOnIdle {
            assertEquals(setOf(dev, travel), filterState?.applied?.folders)
            assertEquals(MailScope.AllMail, loaded.last())
        }
        compose.onNode(hasText("Dev") and hasContentDescription("Folders:", substring = true)).assertIsDisplayed()
        // Closing search returns the folder page to its own scope and clears search folders.
        compose.onNodeWithContentDescription("Back to inbox").performClick()
        compose.runOnIdle {
            assertEquals(InboxFilters(), filterState?.applied)
            assertEquals(null, loaded.last())
        }
    }

    @Test
    fun folderSearchCanClearItsFolderToSearchAllMail() {
        val loaded = mutableListOf<MailScope?>()
        val dev = FolderRef("ada", "dev", includeSubfolders = false)
        var filterState: InboxFilterState? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                val filters = remember { InboxFilterState(scope = scope, counter = { _, _ -> 12 }) }
                filterState = filters
                OtusTheme {
                    InboxScreen(
                        emptyList(),
                        context =
                            inboxContext {
                                title = "Dev"
                                emptyTitle = "Dev is empty"
                                this.filters = filters
                                filterOptions =
                                    FilterPanelFixtures.options.copy(folders = false, kind = ListKind.Folder)
                                mailboxSearch = MailboxSearch(load = { loaded += it })
                                searchFolders = setOf(dev)
                            },
                    )
                }
            }
        }
        compose.openSearch("Search Dev")
        compose.searchField().performTextInput("invoice")
        compose.searchField().performImeAction()
        compose.onNodeWithContentDescription("Open filters").performClick()
        compose.onNodeWithContentDescription("Folders, Dev, opens page").performScrollTo().performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Folders, All folders, opens page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show 12 mails").performClick()
        compose.runOnIdle {
            assertEquals(InboxFilters(allFolders = true), filterState?.applied)
            assertEquals(MailScope.AllMail, loaded.last())
        }
        compose.onNodeWithText("All folders").assertIsDisplayed()
    }
}
