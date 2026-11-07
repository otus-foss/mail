package com.otus.mail

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The search room's filter chips and the People and Accounts sheets they open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchRoomScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openSearch() {
        compose.showControlInbox(mailboxSearch = ControlPanelFixtures.mailboxSearch)
        compose.openSearch()
    }

    @Test
    fun filterChips() = compose.screenshot("search_room/chips")

    @Test
    fun peopleSheet() {
        compose.onNodeWithText("People").performClick()
        compose.pickRow("grace.hopper@example.org").performClick()
        compose.screenshot("search_room/people")
    }

    @Test
    fun pickedPerson() {
        compose.onNodeWithText("People").performClick()
        compose.pickRow("grace.hopper@example.org").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.screenshot("search_room/picked")
    }

    @Test
    fun accountsSheet() {
        compose.onNodeWithContentDescription("All accounts, Inbox").performScrollTo().performClick()
        compose.pickRow("Archive").performClick()
        compose.screenshot("search_room/accounts")
    }
}
