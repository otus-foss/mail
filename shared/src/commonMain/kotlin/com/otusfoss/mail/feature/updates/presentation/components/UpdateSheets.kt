package com.otusfoss.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.shared.settings.components.ChangeLogHistorySheet
import com.otusfoss.core.designsystem.shared.settings.components.ChangeLogRelease
import com.otusfoss.core.designsystem.shared.settings.components.ChangeLogSection
import com.otusfoss.core.designsystem.shared.settings.components.ChangeLogSheet
import com.otusfoss.mail.feature.updates.domain.ChangeGroup
import com.otusfoss.mail.feature.updates.presentation.NightlyUpdateState

/**
 * What changed since the installed build in [update], with Install update, which runs [onInstall],
 * and Changelog, which lists every nightly. [onDismiss] closes the sheet.
 */
@Composable
fun UpdateSheets(
    update: NightlyUpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    ChangeLogSheet(
        "Update",
        update.changes.sections(),
        actionLabel = "Install update",
        onAction = {
            onDismiss()
            onInstall()
        },
        secondaryLabel = "Changelog",
        onSecondary = { historyOpen = true },
        onDismiss = onDismiss,
    )
    if (historyOpen) {
        ChangeLogHistorySheet(
            "Changelog",
            update.history.map { ChangeLogRelease(it.date, it.changes.sections(), it.installed) },
            onDismiss = { historyOpen = false },
        )
    }
}

private fun List<ChangeGroup>.sections() = map { ChangeLogSection(it.area, it.changes) }
