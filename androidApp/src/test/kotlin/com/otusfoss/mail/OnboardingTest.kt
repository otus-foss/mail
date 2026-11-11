package com.otusfoss.mail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.otusfoss.mail.core.settings.domain.MailBehavior
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingTest {
    /** Runs before the activity starts, so the app reads a device that has not finished the first run. */
    @get:Rule(order = 0)
    val firstRun =
        object : ExternalResource() {
            override fun before() {
                AndroidMailBehaviorStore(ApplicationProvider.getApplicationContext())
                    .write(MailBehavior(onboarded = false))
            }
        }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aDeviceWithoutAnAccountOpensOnTheFirstRunAndCannotSkipTheSignIn() {
        compose.waitUntil(
            WAIT_MILLIS,
        ) { compose.onAllNodesWithText("Connect an account").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithText("Your inbox is empty").assertDoesNotExist()
        compose.onNodeWithText("Connect an account").performClick()
        compose.onNodeWithText("Connect your account").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 2 of 6").assertIsDisplayed()
        // Without an account the step offers no way on, only the sign-in.
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Step 1 of 6").assertIsDisplayed()
    }

    @Test
    fun theWelcomeStepSaysWhichProvidersWork() {
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodesWithText("Which providers work?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Which providers work?").performClick()
        compose.onNodeWithText("Stalwart").assertIsDisplayed()
        compose.onNodeWithText("Any other JMAP server").assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
