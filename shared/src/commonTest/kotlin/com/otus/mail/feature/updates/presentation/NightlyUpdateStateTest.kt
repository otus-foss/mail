package com.otus.mail.feature.updates.presentation

import com.otus.mail.feature.updates.domain.NightlyBuild
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightlyUpdateStateTest {
    private val build = NightlyBuild(142, "https://x/142.apk")

    @Test
    fun aNewerBuildIsOffered() =
        runTest {
            val state = NightlyUpdateState({ build }, installed = 141)
            state.check()
            assertEquals(build, state.available)
        }

    @Test
    fun theInstalledBuildIsNotOffered() =
        runTest {
            val state = NightlyUpdateState({ build }, installed = 142)
            state.check()
            assertNull(state.available)
        }

    @Test
    fun aFailedCheckStaysHidden() =
        runTest {
            val state = NightlyUpdateState({ error("offline") }, installed = 1)
            state.check()
            assertNull(state.available)
        }
}
