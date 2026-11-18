package com.otusfoss.mail.feature.notifications.presentation

import com.otusfoss.mail.feature.notifications.domain.InstantPush
import com.otusfoss.mail.feature.notifications.domain.PushDeliveryStatus
import com.otusfoss.mail.feature.notifications.domain.PushDistributor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsStateTest {
    private val ntfy = PushDistributor("io.heckel.ntfy", "ntfy")
    private val google = PushDistributor("google", "Google Play services", isGoogle = true)
    private val push = Push(listOf(ntfy, google))

    private fun TestScope.suggesting(distributor: PushDistributor): NotificationSettingsState =
        NotificationSettingsState(push, this).apply { suggest(distributor) }

    @Test
    fun aSuggestionShowsAsChosenWithoutUsingIt() =
        runTest {
            val state = suggesting(ntfy)
            advanceUntilIdle()
            assertEquals(ntfy, state.shown)
            assertNull(state.current)
            assertEquals(emptyList(), push.used)
        }

    @Test
    fun usingTheSuggestionUsesItOnce() =
        runTest {
            val state = suggesting(ntfy)
            state.useSuggestion()
            state.useSuggestion()
            advanceUntilIdle()
            assertEquals(listOf<PushDistributor?>(ntfy), push.used)
            assertEquals(ntfy, state.current)
        }

    @Test
    fun tappingAnotherChoiceUsesThatOne() =
        runTest {
            val state = suggesting(ntfy)
            state.choose(google)
            state.useSuggestion()
            advanceUntilIdle()
            assertEquals(listOf<PushDistributor?>(google), push.used)
            assertEquals(google, state.shown)
        }

    @Test
    fun tappingOffKeepsTheSuggestionUnused() =
        runTest {
            val state = suggesting(ntfy)
            state.choose(null)
            state.suggest(ntfy)
            state.useSuggestion()
            advanceUntilIdle()
            assertEquals(listOf<PushDistributor?>(null), push.used)
            assertNull(state.shown)
        }

    @Test
    fun aSavedServiceIsNotReplacedByTheSuggestion() =
        runTest {
            push.saved = google
            val state = suggesting(ntfy)
            advanceUntilIdle()
            state.useSuggestion()
            advanceUntilIdle()
            assertEquals(emptyList(), push.used)
            assertEquals(google, state.shown)
        }

    /** Records each service [use] receives. */
    private class Push(
        private val installed: List<PushDistributor>,
    ) : InstantPush {
        val used = mutableListOf<PushDistributor?>()
        var saved: PushDistributor? = null

        override suspend fun distributors() = installed

        override suspend fun current() = saved

        override suspend fun use(distributor: PushDistributor?) {
            used += distributor
            saved = distributor
        }

        override suspend fun statuses() = emptyMap<String, PushDeliveryStatus>()

        override suspend fun forget(loginId: String) = Unit
    }
}
