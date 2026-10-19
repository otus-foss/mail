package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.feature.inbox.domain.DateFilter
import com.otus.mail.feature.inbox.domain.InboxFilters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the filter panel's pages and the pill row do, without comparing screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class FilterPagesBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun customDropsThePresetBeforeARangeIsPicked() {
        compose.showFilterPanel(InboxFilters(date = DateFilter.Last7))
        compose.openFilterPage("Date, ")
        compose.onNodeWithText("Custom", useUnmergedTree = true).performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Date, Any time, ", substring = true).assertExists()
    }

    @Test
    fun anAccountThePageNoLongerOffersKeepsItsPill() {
        compose.showFilterPanel(InboxFilters(account = "gone"), open = false)
        compose.onNodeWithText("gone").assertIsDisplayed()
    }
}
