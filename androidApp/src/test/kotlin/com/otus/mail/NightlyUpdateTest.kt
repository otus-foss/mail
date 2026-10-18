package com.otus.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otus.mail.app.App
import com.otus.mail.app.MailServices
import com.otus.mail.app.NightlyUpdates
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.NightlyFeed
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.mail.feature.updates.domain.UpdateInstaller
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

    private val installed = mutableListOf<String>()

    private fun show(build: InstalledBuild) {
        val services =
            MailServices(
                nightly =
                    NightlyUpdates(
                        createFeed = {
                            NightlyFeed {
                                listOf(
                                    NightlyBuild(142, APK, listOf("- feat(sidebar): show the owl"), "2026-10-18"),
                                )
                            }
                        },
                        installer = UpdateInstaller { download, _ -> installed += download },
                    ),
            )
        compose.activity.setContent { App(services = services, build = build) }
        compose.onNodeWithContentDescription("Open navigation").performClick()
    }

    @Test
    fun aNightlyBuildShowsTheChangesAndInstallsTheNewerNightly() {
        show(InstalledBuild(ReleaseChannel.Nightly, "17.10.26", 141, "abc1234"))
        compose.onNodeWithText("Nightly").assertIsDisplayed()
        compose.onNodeWithContentDescription(UPDATE_141).assertIsDisplayed().performClick()
        compose.onNodeWithText("Show the owl").assertIsDisplayed()
        compose.onNodeWithText("Changelog").performClick()
        compose.onNodeWithText("18.10.26").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Install update").performClick()
        compose.waitUntil(TIMEOUT) { installed.isNotEmpty() }
        assertEquals(listOf(APK), installed)
    }

    @Test
    fun aDevBuildShowsItsCommitAndOffersNoNightly() {
        show(InstalledBuild(ReleaseChannel.Dev, "1.0", 141, "abc1234"))
        compose.onNodeWithText("Dev").assertIsDisplayed()
        compose.onNodeWithText("abc1234").assertIsDisplayed()
        compose.onNodeWithContentDescription(UPDATE_141).assertDoesNotExist()
    }

    private companion object {
        const val UPDATE_141 = "Update available, 17.10.26 installed"
        const val TIMEOUT = 5_000L
        const val APK = "https://x/142.apk"
    }
}
