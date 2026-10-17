package com.otus.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.otus.designsystem.components.SidebarFooter
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.NightlyBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel

/**
 * The sidebar footer for the installed [build]. A newer nightly [update] marks the version, and
 * tapping the version then opens the update's APK link. The browser downloads it, and Android's
 * installer takes over from the download.
 */
@Composable
fun BuildFooter(
    build: InstalledBuild,
    update: NightlyBuild?,
) {
    val uriHandler = LocalUriHandler.current
    SidebarFooter(
        build.channel.name,
        build.versionLabel,
        onUpdate = update?.let { { uriHandler.openUri(it.download) } },
    )
}

/** A local build has no release number, so its commit tells builds apart. */
internal val InstalledBuild.versionLabel: String
    get() =
        when (channel) {
            ReleaseChannel.Dev -> commit
            ReleaseChannel.Nightly -> "Build $versionCode"
            ReleaseChannel.Stable -> versionName
        }
