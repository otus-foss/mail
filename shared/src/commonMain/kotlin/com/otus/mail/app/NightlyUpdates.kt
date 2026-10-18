package com.otus.mail.app

import com.otus.mail.feature.updates.data.GitHubNightlyFeed
import com.otus.mail.feature.updates.domain.NightlyFeed
import com.otus.mail.feature.updates.domain.UpdateInstaller
import io.ktor.client.HttpClient

/** Where nightly builds come from, and how the platform installs one; null [installer] where it cannot. */
class NightlyUpdates(
    createFeed: () -> NightlyFeed = { GitHubNightlyFeed(HttpClient()) },
    internal val installer: UpdateInstaller? = null,
) {
    internal val feed by lazy(createFeed)
}
