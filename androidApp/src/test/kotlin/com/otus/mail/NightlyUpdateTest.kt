package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.NightlyFeed
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NightlyUpdateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val opened = mutableListOf<String>()
    private val uriHandler =
        object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }

    private fun show(installed: Int?) {
        val services = MailServices(createNightlyFeed = { NightlyFeed { NightlyBuild(142, "https://x/142.apk") } })
        compose.activity.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                App(services = services, nightlyBuild = installed)
            }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
    }

    @Test
    fun aNightlyBuildOffersTheNewerNightlyAndOpensItsApk() {
        show(installed = 141)
        compose.onNodeWithText("Get nightly 142").assertIsDisplayed().performClick()
        assertEquals(listOf("https://x/142.apk"), opened)
    }

    @Test
    fun otherBuildsOfferNoNightly() {
        show(installed = null)
        compose.onNodeWithText("All accounts").assertIsDisplayed()
        compose.onNodeWithText("Get nightly 142").assertDoesNotExist()
    }
}
