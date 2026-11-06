package com.otus.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.sidebar.components.SidebarFooter
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.mail.feature.updates.presentation.NightlyUpdateState

/**
 * The sidebar footer for the installed [build]. A newer nightly in [update] marks the version, and
 * tapping the version runs [onOpenUpdate]. The footer shows the download's progress.
 */
@Composable
fun BuildFooter(
    build: InstalledBuild,
    update: NightlyUpdateState?,
    onOpenUpdate: () -> Unit,
) {
    SidebarFooter(
        build.channel.name,
        build.versionLabel,
        onUpdate = update?.available?.let { { onOpenUpdate() } },
        updateProgress = update?.progress,
    )
}

/** A local build has no release date, so its commit tells builds apart. */
internal val InstalledBuild.versionLabel: String
    get() =
        when (channel) {
            ReleaseChannel.Dev -> commit
            ReleaseChannel.Nightly, ReleaseChannel.Stable -> versionName
        }
