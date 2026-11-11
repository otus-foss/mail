package com.otusfoss.mail.feature.updates.domain

/**
 * A published nightly build. [number] grows with each build, [download] fetches its APK, [notes]
 * are its release notes, one `- type(scope): subject` commit line each, and [released] is its
 * publishing date as `yyyy-mm-dd` in UTC. [sha256] is the APK's published SHA-256 in lowercase hex.
 */
data class NightlyBuild(
    val number: Int,
    val download: String,
    val notes: List<String> = emptyList(),
    val released: String = "",
    val sha256: String = "",
)

/** The published nightly builds, newest first; empty when none is published. */
fun interface NightlyFeed {
    suspend fun releases(): List<NightlyBuild>
}

/**
 * Downloads a build's APK from [download] and hands it to the platform installer once its SHA-256
 * matches [sha256]; a mismatch fails the install.
 */
fun interface UpdateInstaller {
    /** Reports the download's share from 0 to 1 to [onProgress], and returns once the installer has the APK. */
    suspend fun install(
        download: String,
        sha256: String,
        onProgress: (Float) -> Unit,
    )
}
