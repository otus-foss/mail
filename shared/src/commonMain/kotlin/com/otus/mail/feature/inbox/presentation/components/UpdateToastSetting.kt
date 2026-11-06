package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.shared.settings.components.SettingToggleRow
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/** The Notifications setting that turns off the toast saying a newer build is out. */
@Composable
internal fun UpdateToastSetting(state: MailBehaviorState) {
    SettingToggleRow(
        UPDATE_TOAST_TITLE,
        checked = state.behavior.updateToasts,
        onCheckedChange = { on -> state.update { it.copy(updateToasts = on) } },
        description = "When a new version is out",
    )
}

/** The setting's name, which settings search finds too. */
const val UPDATE_TOAST_TITLE = "Update toast"
