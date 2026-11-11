package com.otusfoss.mail.feature.mailsettings.presentation.components

import com.otusfoss.core.settings.presentation.components.ToggleRowSpec
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState

/** The Notifications setting that turns off the toast saying a newer build is out. */
fun updateToastRow(state: MailBehaviorState): ToggleRowSpec =
    ToggleRowSpec(
        "Update toast",
        checked = state.behavior.updateToasts,
        onCheckedChange = { on -> state.update { it.copy(updateToasts = on) } },
        description = "When a new version is out",
        keywords = listOf("New version"),
    )
