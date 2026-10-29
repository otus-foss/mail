package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Removing a connected account asks first; only confirming forgets it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountRemovalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = FakeFolderServer()

    private fun openAccounts() {
        compose.runOnUiThread { compose.activity.setContent { App(services = server.services()) } }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Email accounts").performClick()
    }

    @Test
    fun cancellingKeepsTheAccountAndConfirmingRemovesIt() {
        openAccounts()
        compose.onNodeWithContentDescription("Remove Ada").performClick()
        compose.onNodeWithText("Remove Ada?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove Ada?").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Ada").assertIsDisplayed()

        compose.onNodeWithContentDescription("Remove Ada").performClick()
        compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForNode(hasText("No accounts connected yet."))
        compose.onNodeWithContentDescription("Remove Ada").assertDoesNotExist()
    }
}
