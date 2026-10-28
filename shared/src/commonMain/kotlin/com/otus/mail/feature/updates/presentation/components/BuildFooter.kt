package com.otus.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.designsystem.shared.settings.components.ChangeLogHistorySheet
import com.otus.designsystem.shared.settings.components.ChangeLogRelease
import com.otus.designsystem.shared.settings.components.ChangeLogSection
import com.otus.designsystem.shared.settings.components.ChangeLogSheet
import com.otus.designsystem.shared.sidebar.components.SidebarFooter
import com.otus.mail.feature.updates.domain.ChangeGroup
import com.otus.mail.feature.updates.domain.InstalledBuild
import com.otus.mail.feature.updates.domain.ReleaseChannel
import com.otus.mail.feature.updates.presentation.NightlyUpdateState

/**
 * The sidebar footer for the installed [build]. A newer nightly in [update] marks the version, and
 * tapping the version opens what changed since [build]. Its Install update button runs
 * [onInstall], and the footer shows the download's progress. Changelog there lists every nightly.
 */
@Composable
fun BuildFooter(
    build: InstalledBuild,
    update: NightlyUpdateState?,
    onInstall: () -> Unit,
) {
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    SidebarFooter(
        build.channel.name,
        build.versionLabel,
        onUpdate = update?.available?.let { { notesOpen = true } },
        updateProgress = update?.progress,
    )
    if (notesOpen && update != null) {
        ChangeLogSheet(
            "Update",
            update.changes.sections(),
            actionLabel = "Install update",
            onAction = {
                notesOpen = false
                onInstall()
            },
            secondaryLabel = "Changelog",
            onSecondary = { historyOpen = true },
            onDismiss = { notesOpen = false },
        )
    }
    if (historyOpen && update != null) {
        ChangeLogHistorySheet(
            "Changelog",
            update.history.map { ChangeLogRelease(it.date, it.changes.sections(), it.installed) },
            onDismiss = { historyOpen = false },
        )
    }
}

private fun List<ChangeGroup>.sections() = map { ChangeLogSection(it.area, it.changes) }

/** A local build has no release date, so its commit tells builds apart. */
internal val InstalledBuild.versionLabel: String
    get() =
        when (channel) {
            ReleaseChannel.Dev -> commit
            ReleaseChannel.Nightly, ReleaseChannel.Stable -> versionName
        }
