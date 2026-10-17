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
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.NightlyFeed
import com.otus.mail.feature.updates.domain.ReleaseChannel
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

    private fun show(installed: InstalledBuild) {
        val services = MailServices(createNightlyFeed = { NightlyFeed { NightlyBuild(142, "https://x/142.apk") } })
        compose.activity.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                App(services = services, build = installed)
            }
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
    }

    @Test
    fun aNightlyBuildOffersTheNewerNightlyAndOpensItsApk() {
        show(InstalledBuild(ReleaseChannel.Nightly, "nightly-141", 141, "abc1234"))
        compose.onNodeWithText("Nightly").assertIsDisplayed()
        compose.onNodeWithContentDescription(UPDATE_141).assertIsDisplayed().performClick()
        assertEquals(listOf("https://x/142.apk"), opened)
    }

    @Test
    fun aDevBuildShowsItsCommitAndOffersNoNightly() {
        show(InstalledBuild(ReleaseChannel.Dev, "1.0", 141, "abc1234"))
        compose.onNodeWithText("Dev").assertIsDisplayed()
        compose.onNodeWithText("abc1234").assertIsDisplayed()
        compose.onNodeWithContentDescription(UPDATE_141).assertDoesNotExist()
    }

    private companion object {
        const val UPDATE_141 = "Update available, Build 141 installed"
    }
}
