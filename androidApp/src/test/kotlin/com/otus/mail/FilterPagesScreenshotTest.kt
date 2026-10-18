package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.otus.mail.feature.inbox.domain.InboxFilters
import com.otus.mail.feature.inbox.domain.OwnAddress
import com.otus.mail.feature.inbox.domain.SizeFilter
import com.otus.mail.feature.inbox.domain.TypeFacet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Each page the filter panel's rows and People lines open, compared with src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterPagesScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fromPage() {
        compose.showFilterPanel(InboxFilters(from = setOf(FilterPanelFixtures.grace)))
        compose.openFilterPage("From: ")
        compose.filterShot("filter_panel_page_from")
    }

    @Test
    fun toPage() {
        compose.showFilterPanel(InboxFilters(sentTo = setOf(OwnAddress("ada", "otus@rubylabs.design"))))
        compose.openFilterPage("To: ")
        compose.filterShot("filter_panel_page_to")
    }

    @Test
    fun accountPage() {
        compose.showFilterPanel(options = FilterPanelFixtures.twoAccounts)
        compose.openFilterPage("Account, ")
        compose.filterShot("filter_panel_page_account")
    }

    @Test
    fun foldersPageNestedAndSearching() {
        compose.showFilterPanel()
        compose.openFilterPage("Folders, ")
        compose.onNodeWithContentDescription("Search folders").performTextInput("inv")
        compose.waitForIdle()
        compose.filterShot("filter_panel_page_folders")
    }

    @Test
    fun datePageCustomRange() {
        compose.showFilterPanel()
        compose.openFilterPage("Date, ")
        compose.onNodeWithText("Custom").performClick()
        compose.waitForIdle()
        compose.filterShot("filter_panel_page_date_custom")
    }

    @Test
    fun metadataPage() {
        compose.showFilterPanel(InboxFilters(types = setOf(TypeFacet.Newsletters), size = SizeFilter.Over5Mb))
        compose.openFilterPage("Metadata, ")
        compose.filterShot("filter_panel_page_metadata")
    }

    @Test
    fun backOnAPageReturnsToTheMainPage() {
        compose.showFilterPanel()
        compose.openFilterPage("Metadata, ")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Filters").assertIsDisplayed()
        compose.onNodeWithContentDescription("Metadata, ", substring = true).assertIsDisplayed()
    }
}
