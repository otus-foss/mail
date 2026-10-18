package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.otus.designsystem.theme.OtusTheme
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.ShowFacet
import com.otus.mail.feature.inbox.presentation.InboxFilterState
import com.otus.mail.feature.inbox.presentation.screens.InboxScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The inbox filter panel and the pill row it fills, compared with src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterPanelScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val selections =
        InboxFilters(
            show = setOf(ShowFacet.Unread),
            sentTo = setOf(OwnAddress("ada", "otus@rubylabs.design")),
            folders = setOf(FolderRef("ada", "acme", includeSubfolders = false)),
            date = DateFilter.Last7,
        )

    /** The inbox with [applied] filters, the panel open while [open], counting [total] messages. */
    private fun show(
        applied: InboxFilters = InboxFilters(),
        open: Boolean = true,
        total: Long? = 12,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val scope = rememberCoroutineScope()
                val filters = remember { InboxFilterState(applied, scope, counter = { total }) }
                OtusTheme {
                    InboxScreen(
                        FilterPanelFixtures.messages,
                        filters = filters,
                        filterOptions = FilterPanelFixtures.options,
                    )
                }
            }
        }
        if (open) compose.onNodeWithContentDescription("Open filters", substring = true).performClick()
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun panelDefault() {
        show()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        shot("filter_panel_default")
    }

    @Test
    fun panelWithSelections() {
        show(selections)
        shot("filter_panel_selections")
    }

    @Test
    fun allFoldersNestedAndSearching() {
        show()
        compose.onNodeWithContentDescription("All folders…").performClick()
        compose.waitForIdle()
        shot("filter_panel_all_folders")
        compose.onNodeWithContentDescription("Search folders").performTextInput("inv")
        compose.waitForIdle()
        shot("filter_panel_all_folders_search")
    }

    @Test
    fun pillRowWithActiveFilters() {
        show(selections, open = false)
        shot("filter_pills_active")
    }

    @Test
    fun noConversations() {
        show(InboxFilters(show = setOf(ShowFacet.Flagged)), total = 0)
        compose.onNodeWithText("No conversations").assertIsNotEnabled()
        shot("filter_panel_no_conversations")
    }
}
