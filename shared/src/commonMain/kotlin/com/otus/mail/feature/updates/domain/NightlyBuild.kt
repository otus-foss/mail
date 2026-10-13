package com.otus.mail.feature.updates.domain

/** A published nightly build. [number] grows with each build; [download] fetches its APK. */
data class NightlyBuild(
    val number: Int,
    val download: String,
)

/** The newest published nightly build, or null when none is published. */
fun interface NightlyFeed {
    suspend fun latest(): NightlyBuild?
}
