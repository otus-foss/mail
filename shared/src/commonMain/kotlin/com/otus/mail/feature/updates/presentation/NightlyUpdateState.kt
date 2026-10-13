package com.otus.mail.feature.updates.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.NightlyFeed
import kotlinx.coroutines.CancellationException

/**
 * Offers a nightly build newer than the [installed] one. A failed check stays silent, because the
 * user did not ask for it, and the next launch checks again.
 */
class NightlyUpdateState(
    private val feed: NightlyFeed,
    private val installed: Int,
) {
    var available by mutableStateOf<NightlyBuild?>(null)
        private set

    suspend fun check() {
        val latest =
            try {
                feed.latest()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        available = latest?.takeIf { it.number > installed }
    }
}
