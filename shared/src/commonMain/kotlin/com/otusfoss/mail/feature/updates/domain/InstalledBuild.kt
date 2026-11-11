package com.otusfoss.mail.feature.updates.domain

/** How a build reaches the user: built locally, from main each night, or as a release. */
enum class ReleaseChannel { Dev, Nightly, Stable }

/**
 * The running build: its [channel], its Android [versionName] and [versionCode], and the short hash
 * of the [commit] it was built from. A nightly build's [versionCode] is its nightly number.
 */
data class InstalledBuild(
    val channel: ReleaseChannel,
    val versionName: String,
    val versionCode: Int,
    val commit: String,
)
