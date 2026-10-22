package com.otus.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.otus.mail.feature.inbox.domain.InboxFilters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the pill row does, without comparing screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
class FilterPagesBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun anAccountThePageNoLongerOffersKeepsItsPill() {
        compose.showFilterPanel(InboxFilters(account = "gone"), open = false)
        compose.onNodeWithText("gone").assertIsDisplayed()
    }
}
