package com.otus.mail

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.otus.designsystem.theme.colors.ThemeBase
import com.otus.designsystem.theme.colors.ThemeChoice
import com.otus.mail.core.messages.domain.DateFilter
import com.otus.mail.core.messages.domain.FilterSort
import com.otus.mail.core.messages.domain.FolderRef
import com.otus.mail.core.messages.domain.OwnAddress
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.messages.domain.ReadStatus
import com.otus.mail.core.messages.domain.SizeFilter
import com.otus.mail.core.messages.domain.TypeFacet
import com.otus.mail.feature.inbox.domain.InboxFilters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The filter panel's main page in each theme, and the pill row it fills, compared with src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterPanelScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val selections =
        InboxFilters(
            status = ReadStatus.Unread,
            quick = setOf(QuickFacet.Flagged, QuickFacet.Attachments),
            from = setOf(FilterPanelFixtures.grace, FilterPanelFixtures.chatGpt),
            folders = setOf(FolderRef("ada", "acme", includeSubfolders = false), FolderRef("ada", "dev")),
            date = DateFilter.Last7,
        )

    private val light = ThemeChoice(ThemeBase.Light).palette
    private val black = ThemeChoice(ThemeBase.Black).palette

    @Test
    fun panelDefault() {
        compose.showFilterPanel()
        compose.onNodeWithContentDescription("Reset filters").assertIsNotEnabled()
        compose.screenshot("filter_panel_default")
    }

    @Test
    fun panelDefaultLight() {
        compose.showFilterPanel(palette = light)
        compose.screenshot("filter_panel_default_light")
    }

    @Test
    fun panelDefaultBlack() {
        compose.showFilterPanel(palette = black)
        compose.screenshot("filter_panel_default_black")
    }

    @Test
    fun panelWithSelections() {
        compose.showFilterPanel(selections, options = FilterPanelFixtures.twoAccounts)
        compose.screenshot("filter_panel_selections")
    }

    @Test
    fun panelWithSelectionsLight() {
        compose.showFilterPanel(selections, options = FilterPanelFixtures.twoAccounts, palette = light)
        compose.screenshot("filter_panel_selections_light")
    }

    @Test
    fun panelWithSelectionsBlack() {
        compose.showFilterPanel(selections, options = FilterPanelFixtures.twoAccounts, palette = black)
        compose.screenshot("filter_panel_selections_black")
    }

    @Test
    fun pillRowWithActiveFilters() {
        val applied =
            selections.copy(
                sentTo = setOf(OwnAddress("ada", "otus@rubylabs.design")),
                account = "ada",
                types = setOf(TypeFacet.Newsletters),
                size = SizeFilter.Over5Mb,
                sort = FilterSort.OldestFirst,
            )
        compose.showFilterPanel(applied, open = false, options = FilterPanelFixtures.twoAccounts)
        compose.screenshot("filter_pills_active")
    }

    @Test
    fun noConversations() {
        compose.showFilterPanel(InboxFilters(quick = setOf(QuickFacet.Flagged)), total = 0)
        compose.onNodeWithContentDescription("No mails").assertIsNotEnabled()
        compose.screenshot("filter_panel_no_conversations")
    }
}
