package com.otusfoss.mail.feature.updates.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.feature.updates.domain.ChangeGroup
import com.otusfoss.mail.feature.updates.domain.NightlyBuild
import com.otusfoss.mail.feature.updates.domain.NightlyFeed
import com.otusfoss.mail.feature.updates.domain.UpdateInstaller
import com.otusfoss.mail.feature.updates.domain.changesIn
import kotlinx.coroutines.CancellationException

/**
 * Offers the newest nightly build past the [installed] one, with the changes of every build in
 * between, and installs it through [installer]. A failed check stays silent, because the user did
 * not ask for it, and the next launch checks again.
 */
class NightlyUpdateState(
    private val feed: NightlyFeed,
    private val installed: Int,
    private val installer: UpdateInstaller,
) {
    var available by mutableStateOf<NightlyBuild?>(null)
        private set

    /** What changed since the installed build. */
    var changes by mutableStateOf(emptyList<ChangeGroup>())
        private set

    /** Every published build with its own changes, newest first. */
    var history by mutableStateOf(emptyList<ReleaseNotes>())
        private set

    /** The download's share from 0 to 1 while it runs, otherwise null. */
    var progress by mutableStateOf<Float?>(null)
        private set

    suspend fun check() {
        val builds = attempt { feed.releases() }.orEmpty().sortedByDescending { it.number }
        val newer = builds.filter { it.number > installed }
        available = newer.firstOrNull()
        changes = changesIn(newer)
        history = builds.map { ReleaseNotes(it.released.shortDate(), it.number <= installed, changesIn(listOf(it))) }
    }

    /** Downloads and installs [available]; false when that failed, so the caller can say so. */
    suspend fun install(): Boolean {
        val build = available ?: return false
        progress = 0f
        return try {
            attempt { installer.install(build.download, build.sha256) { progress = it } } != null
        } finally {
            progress = null
        }
    }

    /** `2026-10-07` as `7.10.26`, the way nightly version names read. */
    private fun String.shortDate(): String {
        val (year, month, day) = split('-').takeIf { it.size == DATE_PARTS } ?: return this
        return "${day.toInt()}.${month.toInt()}.${year.takeLast(2)}"
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val DATE_PARTS = 3
    }
}

/** One build's [date] as d.m.yy, whether the running app already has it, and its [changes]. */
data class ReleaseNotes(
    val date: String,
    val installed: Boolean,
    val changes: List<ChangeGroup>,
)
