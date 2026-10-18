package com.otus.mail.feature.updates.presentation

import com.otus.mail.feature.updates.domain.ChangeGroup
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.UpdateInstaller
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightlyUpdateStateTest {
    private val builds =
        listOf(
            NightlyBuild(143, "https://x/143.apk", listOf("- feat(sidebar): newest"), "2026-10-18"),
            NightlyBuild(142, "https://x/142.apk", listOf("- fix(sidebar): newer"), "2026-10-07"),
            NightlyBuild(141, "https://x/141.apk", listOf("- feat(sidebar): installed"), "2026-10-06"),
        )
    private val installs = mutableListOf<String>()
    private val installer =
        UpdateInstaller { download, onProgress ->
            onProgress(0.5f)
            installs += download
        }

    @Test
    fun theNewestBuildIsOfferedWithEveryChangeSinceTheInstalledOne() =
        runTest {
            val state = NightlyUpdateState({ builds }, installed = 141, installer)
            state.check()
            assertEquals(builds.first(), state.available)
            assertEquals(listOf(ChangeGroup("Sidebar", listOf("Newest", "Newer"))), state.changes)
        }

    @Test
    fun theHistoryListsEachBuildByDateAndMarksTheInstalledOne() =
        runTest {
            val state = NightlyUpdateState({ builds }, installed = 141, installer)
            state.check()
            assertEquals(
                listOf(
                    ReleaseNotes("18.10.26", false, listOf(ChangeGroup("Sidebar", listOf("Newest")))),
                    ReleaseNotes("7.10.26", false, listOf(ChangeGroup("Sidebar", listOf("Newer")))),
                    ReleaseNotes("6.10.26", true, listOf(ChangeGroup("Sidebar", listOf("Installed")))),
                ),
                state.history,
            )
        }

    @Test
    fun theInstalledBuildIsNotOffered() =
        runTest {
            val state = NightlyUpdateState({ builds }, installed = 143, installer)
            state.check()
            assertNull(state.available)
        }

    @Test
    fun aFailedCheckStaysHidden() =
        runTest {
            val state = NightlyUpdateState({ error("offline") }, installed = 1, installer)
            state.check()
            assertNull(state.available)
        }

    @Test
    fun installingDownloadsTheOfferedBuildAndClearsTheProgress() =
        runTest {
            val state = NightlyUpdateState({ builds }, installed = 141, installer)
            state.check()
            assertTrue(state.install())
            assertEquals(listOf("https://x/143.apk"), installs)
            assertNull(state.progress)
        }

    @Test
    fun aFailedDownloadReportsFalse() =
        runTest {
            val state = NightlyUpdateState({ builds }, installed = 141) { _, _ -> error("offline") }
            state.check()
            assertFalse(state.install())
            assertNull(state.progress)
        }
}
