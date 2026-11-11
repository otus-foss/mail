package com.otusfoss.mail

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otusfoss.mail.app.App
import com.otusfoss.mail.app.MailPlatform
import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.app.NightlyUpdates
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.feature.updates.domain.InstalledBuild
import com.otusfoss.mail.feature.updates.domain.NightlyBuild
import com.otusfoss.mail.feature.updates.domain.NightlyFeed
import com.otusfoss.mail.feature.updates.domain.ReleaseChannel
import com.otusfoss.mail.feature.updates.domain.UpdateInstaller
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

    private fun show(
        build: InstalledBuild,
        behavior: MailBehavior = MailBehavior(),
        openSidebar: Boolean = true,
    ) {
        val services =
            MailServices(
                host =
                    MailPlatform(
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
                    ),
            )
        val behaviorState = MailBehaviorState(initial = behavior)
        compose.activity.setContent { App(services = services, behaviorState = behaviorState, build = build) }
        if (openSidebar) compose.onNodeWithContentDescription("Open navigation").performClick()
    }

    @Test
    fun aNightlyBuildShowsTheChangesAndInstallsTheNewerNightly() {
        show(NIGHTLY_141)
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
    fun aNewerNightlyShowsAToastThatOpensItsChanges() {
        show(NIGHTLY_141, openSidebar = false)
        // The toast's timer runs on frames, so an idle wait would close it; the test steps the clock.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.onNodeWithText(UPDATE_TOAST, substring = true).assertIsDisplayed()
        compose.onNodeWithText("View").performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.onNodeWithText("Show the owl").assertIsDisplayed()
    }

    @Test
    fun turningTheUpdateToastOffHidesIt() {
        show(NIGHTLY_141, MailBehavior(updateToasts = false))
        // The footer's mark shows once the check is done, so the toast would be on screen by then.
        compose.onNodeWithContentDescription(UPDATE_141).assertIsDisplayed()
        compose.onNodeWithText(UPDATE_TOAST, substring = true).assertDoesNotExist()
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
        const val UPDATE_TOAST = "Update available"
        val NIGHTLY_141 = InstalledBuild(ReleaseChannel.Nightly, "17.10.26", 141, "abc1234")
        const val TIMEOUT = 5_000L
        const val SETTLE_MILLIS = 1_000L
        const val APK = "https://x/142.apk"
    }
}
